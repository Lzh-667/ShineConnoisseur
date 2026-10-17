package com.lzh.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lzh.dto.UserDTO;
import com.lzh.mapper.UserVipMapper;
import com.lzh.po.UserVip;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VipIdentityServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                UserVip.class
        );
    }

    @Mock
    private UserVipMapper userVipMapper;

    @Test
    void activeUserIds_filtersInvalidIdsAndQueriesOnlyUnexpiredMemberships() {
        UserVip activeVip = new UserVip();
        activeVip.setUserId(2L);
        when(userVipMapper.selectList(any())).thenReturn(List.of(activeVip));
        VipIdentityService service = new com.lzh.service.impl.VipIdentityServiceImpl(userVipMapper);

        Set<Long> result = service.activeUserIds(Arrays.asList(null, 2L, 2L, 3L));

        assertEquals(Set.of(2L), result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<UserVip>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userVipMapper).selectList(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("user_id") && sqlSegment.contains("IN"));
        assertTrue(sqlSegment.contains("expire_time") && sqlSegment.contains(">"));
    }

    @Test
    void enrich_ignoresNullUsersAndSetsVipFromQueryResult() {
        UserVip activeVip = new UserVip();
        activeVip.setUserId(2L);
        when(userVipMapper.selectList(any())).thenReturn(List.of(activeVip));
        VipIdentityService service = new com.lzh.service.impl.VipIdentityServiceImpl(userVipMapper);
        UserDTO vipUser = new UserDTO();
        vipUser.setId(2L);
        UserDTO normalUser = new UserDTO();
        normalUser.setId(3L);

        service.enrich(Arrays.asList(null, vipUser, normalUser));

        assertTrue(vipUser.getVip());
        assertFalse(normalUser.getVip());
    }

    @Test
    void isVip_withNullUserId_returnsFalseWithoutQuery() {
        VipIdentityService service = new com.lzh.service.impl.VipIdentityServiceImpl(userVipMapper);

        assertFalse(service.isVip(null));

        verify(userVipMapper, never()).selectList(any());
    }
}
