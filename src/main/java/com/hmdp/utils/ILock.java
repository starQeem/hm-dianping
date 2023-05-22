package com.hmdp.utils;

public interface ILock {
    /*
    * 获取锁
    * */
    boolean tryLock(long timeOutSec);
    /*
    * 释放锁
    * */
    void unLock();
}
