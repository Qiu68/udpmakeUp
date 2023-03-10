package com.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @version 1.0
 * @Author:qiu
 * @Description
 * @Date 10:51 2023/3/6
 **/
public class retransmitHandler extends SimpleChannelInboundHandler {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("tcp连接");
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
            ByteBuf buf = (ByteBuf) o;
            System.out.println("服务端收到消息:");
            channelHandlerContext.close();
            channelHandlerContext.fireChannelReadComplete();
            channelHandlerContext.writeAndFlush("12");
    }
}
