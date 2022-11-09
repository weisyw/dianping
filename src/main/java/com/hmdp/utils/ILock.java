package com.hmdp.utils;

/**
 * Author: ww
 * DateTime: 2022/11/8 19:26
 * Description: 锁的基本接口 分布式初级版本
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true代表获取锁成功，false代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
