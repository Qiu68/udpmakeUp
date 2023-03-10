package com.server;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

/**
 * @version 2.0
 * @Author:qiu
 * @Description 服务端向客户端发送文件，加入重传机制
 * @Date 15:51 2023/2/27
 **/
public class CZServer {
    static void sendFileInit(){
        EventLoopGroup work = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(work);
        bootstrap.channel(NioDatagramChannel.class);
        bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(65535));
        //设置udp socket缓冲 100m
        bootstrap.option(ChannelOption.SO_RCVBUF, 100*1024*1024);

        bootstrap.option(ChannelOption.SO_SNDBUF, 1024 * 1024 * 1 );
        //发送缓冲区
        bootstrap.handler(new ChannelInitializer() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                //发送文件处理器
                channel.pipeline().addLast(new CZServerHandler());
            }
        });
        try {
            ChannelFuture future = bootstrap.bind(9999).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            work.shutdownGracefully();
        }

    }
    public static void main(String[] args) throws InterruptedException {
        //FileBody fileBody = new FileBody("569mb.h264", "d:/");
        //retransmitInit();
        sendFileInit();

    }
}
