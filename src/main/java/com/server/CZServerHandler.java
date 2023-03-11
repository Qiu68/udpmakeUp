package com.server;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @version 2.0
 * @Author:qiu
 * @Description 服务器向客户端传输文件处理器,加入重传机制
 * @Date 16:08 2023/2/27
 **/
public class CZServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    static String clientIp = null;
    static Integer clientPort = null;
    static RandomAccessFile raf;
    //Udp发送最大字节数 超过65507就不会被发送
    static int readLength = 40*1024;
    static byte[] bytes = new byte[readLength];
    static int readCount = 0; //读文件的次数
    static int count = 1;
    static long start = 0;
    static long end = 0;
    //确保一个用户只有一个SendTask对象
    static Map<String,SendTask>  map = new ConcurrentHashMap();
    static String uuid;
    static {
        try {

            raf = new RandomAccessFile("/usr/287m.h264","r");

            //读文件的次数
            long fileSize = raf.getChannel().size();
            System.out.println("文件大小:"+fileSize);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) throws Exception {
        byte[] cmdByte = new byte[4];
        byte[] tmp = new byte[1024*1024];
        Channel channel = channelHandlerContext.channel();
        int cmd = 0;
        byte[] lossPacket = new byte[1];
        String file = "/usr/287m.h264";
        SendTask sendTask;
        /**
         * 1.取出客户端ip
         * 2.发送文件
         */
        //取客户端ip、端口
        InetSocketAddress sender = datagramPacket.sender();//取出客户端ip
        clientIp = sender.getHostString();
        clientPort = sender.getPort();
        if (clientIp == null || clientPort == null){
            throw new Exception("获取客户端ip出错！");
        }

        System.out.println("客户端:"+clientIp+":"+clientPort);

        uuid = clientIp + clientPort;

        if (map.get(uuid) == null){
           sendTask = new SendTask( file,clientIp,clientPort,channel);
            map.put(uuid,sendTask);
        }
        else {
            sendTask = map.get(uuid);
        }

        System.out.println("readcount:"+readCount);



        ByteBuf content = datagramPacket.content();
        int length = content.readableBytes();

        byte[] data = new byte[length];
        int dataLength = length-cmdByte.length;

        content.readBytes(data);

        System.arraycopy(data,0,cmdByte,0,cmdByte.length);
        cmd = Util.bytesToInt(cmdByte);
        System.out.println("cmd:"+cmd);

        if (dataLength > 0) {
            lossPacket = new byte[length - cmdByte.length];
            System.arraycopy(data,cmdByte.length,lossPacket,0,
                    data.length-cmdByte.length);
            System.out.println("lossPacket:"+lossPacket.length);
            sendTask.setRetransByte(lossPacket);
        }





        //将客户端ip 端口 发送给客户端
        if (cmd == 0){
            byte[] msg = (clientIp+clientPort).getBytes(StandardCharsets.UTF_8);
            ByteBuf buf1 = ByteBufAllocator.DEFAULT.buffer();
            buf1.writeBytes(msg);
            DatagramPacket dgp = new DatagramPacket(buf1,
                    new InetSocketAddress(clientIp,clientPort));
            channelHandlerContext.writeAndFlush(dgp);
        }

        //发送文件
        if (cmd == 1){
            if (sendTask == null){
                throw new Exception("对象为空!");
            }
            //发送文件
            sendTask.exec();
        }

        //重传数据包
        if (cmd == 2){

            // 一个IP加端口 做一个唯一标识 确保一个用户只有一个SendTask对象
            if (sendTask == null){
                throw new Exception("对象为空!");
            }
            System.out.println("准备重传");
                sendTask.stopSend();
                if (sendTask.getThread().isInterrupted()) {
                    System.out.println("中断成功");
                }

        }

        //将文件最后一个数据包字节数和序列号发送给客户端
        if (cmd == 3){
            //将文件最后一个数据包的id发送给客户端
            readCount = sendTask.getReadCount();
            ByteBuf buf1 = ByteBufAllocator.DEFAULT.buffer();
            byte[] data1 = new byte[4];
            data1 = Util.intToBytes(readCount);
            buf1.writeBytes(data1);
            DatagramPacket dgp = new DatagramPacket(buf1,
                    new InetSocketAddress(clientIp,clientPort));
            channelHandlerContext.writeAndFlush(dgp);
        }


//        channelHandlerContext.close();



    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelActive");
        super.channelActive(ctx);
    }



    void test(){

        /**
         * 将文件内容封装为对象，序列化后发送
         */
//       long start =0l;
//       long end = 0l;
//       int eventPageSize = 28*1024;
//       int pageCount = 1; //分段次数
//        fis = new FileInputStream(fileBody.getFilePath()+fileBody.getFileName());
//        FileChannel channel = fis.getChannel();
//        fileBody.setAvailable(fis.available());//获取文件总大小
//
//        pageCount = (int) (fileBody.getAvailable() % eventPageSize==0 //获取文件分段次数
//                ? fileBody.getAvailable() % eventPageSize
//                : fileBody.getAvailable() / eventPageSize +1l);
//
//
//        fileBody.setEventPageSize(eventPageSize);
//
//
//        for (int i=1;i<=pageCount;i++){
//            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(1,68*1024);
//            ByteBuffer buffer = ByteBuffer.allocate(28*1024);
//            fileBody.setOrder(i);//设置数据包次序
//            int readLength = channel.read(buffer);//读buffer.length个字节
//            byte[] data = new byte[readLength];
//            buffer.flip();
//            buffer.get(data);
//            fileBody.setData(data);
//            System.out.println("数据大小:"+data.length);
//            start = start + eventPageSize;
//            fileBody.setStart(start);
//            byte[] bytes = JSON.toJSONBytes(fileBody);//将对象序列化为字节数组
//            String s = JSON.toJSONString(fileBody);
//            System.out.println("分页次数:"+pageCount);
//            System.out.println(s);
//            System.out.println("对象大小："+bytes.length);
//            buf.writeBytes(bytes);
//            DatagramPacket packet = new DatagramPacket(buf, new InetSocketAddress(clientIp, clientPort));
//            channelHandlerContext.writeAndFlush(packet);
//            buffer.clear();
//
//
//            System.out.println("分页次数:"+pageCount);
////            Thread.sleep(2);
//
//        }
    }
}
