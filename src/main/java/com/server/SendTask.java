package com.server;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.PlatformDependent;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @version 1.0
 * @Author:qiu
 * @Description 发送文件任务
 * @Date 10:09 2023/3/9
 **/
public class SendTask implements Runnable {

    private Thread thread;
    public List<Integer> loss_queue = new ArrayList<>();
    public String file;
    //每次读数据的开始位置
    public long start;
    //每次读数据的结束位置
    public long end;
    //文件字节数
    public long fileLength;
    //是否发送
    private boolean sendFlag = true;
    //读文件的总次数
    private int readCount;
    //读取数据包计数
    private int count = 1;
    //每个数据包的大小
    private int eventPacketSize = 40 * 1024;
    private byte[] tmp = new byte[eventPacketSize];
    private RandomAccessFile raf;
    private String clientIp;
    private Integer clientPort;
    private Channel channel;
    private PacketIndex packetIndex = null;
    private  byte[] retransByte = null;

    public SendTask(String file, String clientIp, Integer clientPort, Channel channel) throws IOException {
        this.file = file;
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.channel = channel;
        init();
    }

    public void setRetransByte(byte[] data){
        this.retransByte = data.clone();
    }

    private void init() throws IOException {
        raf = new RandomAccessFile(file, "r");
        fileLength = raf.length();
        thread = new Thread(this);
        //计算读文件的次数
        readCount = (int) (fileLength % eventPacketSize == 0
                ? fileLength / eventPacketSize
                : fileLength / eventPacketSize + 1);
    }

    public int getReadCount(){
        return readCount;
    }

    @Override
    public void run() {
        if (retransByte != null && retransByte.length > 0){

            try {
                retransmitTask(retransByte);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        else {
            try {
                send();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public Thread getThread() {
        return thread;
    }

    //发送文件
    public void send() throws IOException, InterruptedException {

        while (sendFlag) {
            int order = count;
            if (order > readCount){
                if (retransByte != null && retransByte.length > 0) {
                    System.out.println("中断，开始执行重传");
                    retransmitTask(retransByte);
                }
                else {
                    System.out.println("退出1");
                    continue;
                }
            }
            //收到中断指令
            if (thread.isInterrupted()){
                System.out.println("中断1");
                if (retransByte != null && retransByte.length > 0) {
                    retransmitTask(retransByte);
                }
            }
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(1, 42 * 1024);
            int length = 0;
            raf.seek((count-1)*40960L);
            try {
                length = raf.read(tmp);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (length == -1) {
                //文件读完了，看看还有没有重传的包
                if (retransByte != null && retransByte.length > 0) {
                    retransmitTask(retransByte);
                }
                //直接退出
                else {
                    System.out.println("退出2");
                    continue;
                }
            }
            System.out.println("length:"+length);
            byte[] bytes = new byte[length];
            System.arraycopy(tmp, 0, bytes, 0, length);

            byte[] data = new byte[length + 4];
            byte[] headByte = Util.intToBytes(order);
            //将数据包的顺序id写入字节数组  四个字节
            System.arraycopy(headByte, 0, data, 0, 4);

            //将数据包的顺序id和数据包内容拼接在一起
            /**
             * 这里有一个坑
             */
            System.arraycopy(bytes, 0, data, data.length - bytes.length, bytes.length);

            buf.writeBytes(data);
            DatagramPacket datagramPackets = new DatagramPacket(buf,
                    new InetSocketAddress(clientIp, clientPort));
            channel.writeAndFlush(datagramPackets);
            System.out.println("order:" + order + "  发送字节数:" + data.length);
            System.out.println("内存已使用:" + PlatformDependent.usedDirectMemory() + "   内存最大数：" + PlatformDependent.maxDirectMemory());
            count++;
            try {
                Thread.sleep(1);
            }
            //收到中断指令
            catch (InterruptedException e) {
                System.out.println("中断2");
                    if (retransByte != null && retransByte.length > 0) {
                        System.out.println("中断，开始执行重传");
                        retransmitTask(retransByte);

                }
            }


        }
        System.out.println("文件发送完毕");
    }

    public void retransmitTask(byte[] bytes) throws InterruptedException {
        byte[] lossPackets = bytes.clone();
        //暂停发送
        System.out.println("开始重传！");
        //开始重传
        /**
         * 数据包重传
         */
        PacketIndex packetIndex = null;
        byte[] temp = new byte[40 * 1024];
        try {
            packetIndex = new PacketIndex("/usr/",
                    "287m.h264", eventPacketSize);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            System.out.println("data.length:" + lossPackets.length);


            int lossPacketSize = lossPackets.length / 4;

            //将每个丢失的数据包，进行补发
            for (int i = 0; i < lossPacketSize; i++) {
                byte[] lossPacketByte = new byte[4];
                System.out.println("lossPaketByte:" + lossPacketByte.length);
                System.arraycopy(lossPackets, i * 4,
                        lossPacketByte, 0, lossPacketByte.length);

                int lossPacketId = Util.bytesToInt(lossPacketByte);
                System.out.println("loss:" + lossPacketId);

                raf.seek((lossPacketId - 1) * 40960L);
                ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(1, 42 * 1024);
                int order = lossPacketId;
                int length = raf.read(temp);
                byte[] data = new byte[length];
                System.arraycopy(temp, 0, data, 0, length);

                byte[] dataPacket = new byte[length + 4];
                byte[] headByte = Util.intToBytes(order);
                //将数据包的顺序id写入字节数组  四个字节
                System.arraycopy(headByte, 0, dataPacket, 0, 4);

                System.arraycopy(data, 0, dataPacket, dataPacket.length - data.length, data.length);

                buf.writeBytes(dataPacket);
                DatagramPacket datagramPackets = new DatagramPacket(buf,
                        new InetSocketAddress(clientIp, clientPort));
                channel.writeAndFlush(datagramPackets);
                System.out.println("补发数据包:" + lossPacketId +
                        "  字节数:" + dataPacket.length);
                System.out.println(order + "  seek:" + (lossPacketId - 1) * 40960L);
            }
//            Thread.sleep(200);
            retransByte = null;
            //继续发送文件
        return;
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public boolean getSendFlag() {
        return sendFlag;
    }

    public void setSendFlag(boolean flag) {
        this.sendFlag = flag;
    }

    public void stopSend(){
        thread.interrupt();
    }

    public void exec(){
        Thread  thread1 = this.getThread();
        if (thread1 != null) {
            thread1.start();
        }
    }

}
