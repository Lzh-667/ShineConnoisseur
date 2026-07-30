package com.lzh.service;

import com.lzh.common.Result;

public interface IAdminDashboardService {
    void refreshDashboardCache();

    Result dashboard();
}
