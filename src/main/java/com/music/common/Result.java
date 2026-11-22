package com.music.common;

public class Result<T> {
    private boolean success;
    private String msg;
    private T data;

    // 单参数ok，默认消息
    public static <T> Result<T> ok(T data){
        Result<T> r = new Result<>();
        r.success = true;
        r.data = data;
        r.msg = "操作成功";
        return r;
    }

    // 双参数ok，自定义消息 ✅新增这个方法！
    public static <T> Result<T> ok(T data, String msg){
        Result<T> r = new Result<>();
        r.success = true;
        r.data = data;
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> fail(String msg){
        Result<T> r = new Result<>();
        r.success = false;
        r.msg = msg;
        return r;
    }

    // getter setter
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
