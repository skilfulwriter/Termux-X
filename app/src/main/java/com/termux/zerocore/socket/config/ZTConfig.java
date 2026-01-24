package com.termux.zerocore.socket.config;

import android.content.Context;
import java.io.PrintWriter;

public interface ZTConfig {
    // 获取相对应的命令执行
    String getCommand(Context context, String command);
    
    // 执行命令 (支持流式输出)
    // 默认实现调用 getCommand 并一次性输出，子类可重写此方法以支持流式
    default void execute(Context context, String command, PrintWriter out) {
        String result = getCommand(context, command);
        if (result != null) {
            out.println(result);
        }
    }

    // 当前的对应ID
    int getId();
    // 是否需要转发
    boolean isForWard();
    // 转发页面执行
    String getCommandForWard(Context context, String command);
}
