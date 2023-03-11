package com.client;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @version 1.0
 * @Author:qiu
 * @Description
 * @Date 9:51 2023/2/28
 **/
public class ClientReceiveFile {


    static int lastPacketId = 0;
    static int order = 1;
    //当前接收数据包的序列号
    static int now_receive_packetId;
    //上一次接收数据包的序列号
    static int before_receive_packetId;
    //数据包丢失队列
    static CopyOnWriteArrayList<Integer> loss_queue;
    //cmd 0 返回客户端地址 1 传输文件 2 重传 3 获取文件最后一个包的大小和序列号
    static int cmd = 0;
    static InetSocketAddress serverHost;
    static DatagramSocket datagramSocket;
    static DatagramPacket sendDatagramPacket;
    static DatagramPacket receiveDatagramPacket;
    static byte[] cmdByte = new byte[4];
    static byte[] receiveMsg = new byte[40 * 1024 + 4];
    //接收flag
    static boolean receiveFlag = true;
    static int maxOrder = 1;
    static int count = 1;

    static {
        try {
            serverHost = new InetSocketAddress("192.168.50.5", 9999);
            datagramSocket = new DatagramSocket();
            loss_queue = new CopyOnWriteArrayList<>();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) throws IOException {

        cmd = 3;
        cmdByte = Util.intToBytes(cmd);


         sendDatagramPacket = new DatagramPacket(cmdByte,
                cmdByte.length, serverHost);
        datagramSocket.send(sendDatagramPacket);

        receiveDatagramPacket = new DatagramPacket(receiveMsg,
                receiveMsg.length);

        datagramSocket.receive(receiveDatagramPacket);
        byte[] data1 = new byte[receiveDatagramPacket.getData().length];
        System.arraycopy(receiveDatagramPacket.getData(),
                0, data1, 0, data1.length);
        lastPacketId = Util.bytesToInt(data1);
        System.out.println("最后一个包的大小：" + data1.length + "内容：" + lastPacketId);


        datagramSocket.setReceiveBufferSize(1024 * 1024 * 300);

        cmd = 1;
        cmdByte = Util.intToBytes(cmd);
        sendDatagramPacket = new DatagramPacket(cmdByte,
                cmdByte.length, serverHost);
        datagramSocket.send(sendDatagramPacket);

        //1000ms就将丢失队列的数据发送给服务端
        timeMillisCountTask();
        try {
            receive();
        }
        catch (SocketTimeoutException e){
            System.out.println("超时");
            System.out.println("maxOrder:"+maxOrder);
            if (maxOrder < lastPacketId){
                for (int i = maxOrder +1 ; i<=lastPacketId ;i++) {
                    loss_queue.add(i);
                }
                receive();
            }
        }


    }


    public static void receive() throws IOException {
        RandomAccessFile raf = new RandomAccessFile("d:/2/287m.h264", "rw");
        long startReceiveTime = System.currentTimeMillis();
        datagramSocket.setSoTimeout(3000);
            while (receiveFlag) {
                if (System.currentTimeMillis() - startReceiveTime > 80000) {
                    System.out.println("最后一个包没有收到");

                }
                datagramSocket.receive(receiveDatagramPacket);
                int readLength = receiveDatagramPacket.getLength();
                byte[] orderByte = new byte[4];
                byte[] tmp = new byte[readLength];
                tmp = receiveDatagramPacket.getData();
                //获取数据包的顺序id
                System.arraycopy(tmp, 0, orderByte, 0, 4);
                //将顺序id转换成int
                order = Util.bytesToInt(orderByte);
                //将顺序id转换成int
                System.out.println("order:" + order + "  接收数据:" + readLength + "  " +
                        "接收到的次数:" + count++);

                if (maxOrder < order){
                    maxOrder = order;
                }
                now_receive_packetId = order;
                if (now_receive_packetId == 1) {
                    before_receive_packetId = 0;
                }
                //数据包顺序到达的情况
                if (now_receive_packetId - before_receive_packetId == 1) {
                    byte[] data = new byte[readLength - orderByte.length];
                    //去掉顺序id的四个字节，然后将数据包内容写入文件
                    System.arraycopy(tmp, 4
                            , data, 0, readLength - orderByte.length);
                    //移动到这个数据包写入文件的位置
                    raf.seek((order - 1) * 40960L);
                    //System.out.println("seek:"+(order-1) * data.length);
                    raf.write(data);
                }

                //丢包或者乱序的情况
                else if (now_receive_packetId - before_receive_packetId > 1) {
                    for (int i = before_receive_packetId + 1; i < now_receive_packetId; i++) {
                        //暂时加入丢失队列
                        loss_queue.add(i);
                    }
                }


                if (order == lastPacketId){
                    removeId(loss_queue,order);
                }

                if (now_receive_packetId < before_receive_packetId) {
                    //从丢失队列中移除
                    removeId(loss_queue, now_receive_packetId);
                    System.out.println("order:" + order + "seek:" + (order - 1) * 40960L);
                    RandomAccessFile raf2 = new RandomAccessFile("d:/2/287m.h264", "rw");
                    byte[] data = new byte[tmp.length - orderByte.length];
                    //去掉顺序id的四个字节，然后将数据包内容写入文件
                    System.arraycopy(tmp, 4
                            , data, 0, tmp.length - orderByte.length);
                    //移动到这个数据包写入文件的位置
                    raf2.seek((order - 1) * 40960L);
                    raf2.write(data);
                    //这种情况不能更新上一个接收到的包
                    continue;
                }



                //更新before_receive_packetId
                before_receive_packetId = now_receive_packetId;

            }


    }




    public static void retransmitTask(){
        Thread t1 = new Thread(()->{
            int lossPacketId = 0;
            byte[] lossPacketByte = new byte[4];
            byte[] tmp = new byte[1024*1024];
            int count = 1;

            Iterator<Integer> iterator = loss_queue.iterator();

            while (iterator.hasNext()){
                int order = iterator.next();
                lossPacketByte = Util.intToBytes(order);
                System.out.println("lossPacket:" + order);
                //将丢失链表的数据转换成byte数组，拼在一起
                System.arraycopy(lossPacketByte,0,
                        tmp,(count-1) * lossPacketByte.length,
                        lossPacketByte.length);
                count++;
            }

            int lossPacketBytesLength = count - 1;
            byte[] lossPacketBytes = new byte[lossPacketBytesLength*4];
            System.arraycopy(tmp,0,lossPacketBytes,0,lossPacketBytes.length);
            if (lossPacketBytes.length > 0) {
                try {
                    //不清零
//                    loss_queue.clear();

                    cmd = 2;
                    cmdByte = Util.intToBytes(cmd);

                    byte[] data = new byte[cmdByte.length + lossPacketBytes.length];

                    //将命令写入数据包前四个字节
                    System.arraycopy(cmdByte,0,data,0,cmdByte.length);

                    //将传输内容写入前四个字节后面
                    System.arraycopy(lossPacketBytes,0,data,cmdByte.length,
                            data.length - cmdByte.length);

                    sendDatagramPacket = new DatagramPacket(data,data.length,
                            serverHost);
                    datagramSocket.send(sendDatagramPacket);
                    System.out.println("data.length:"+data.length);
//                    Thread.sleep(100);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


        });
        t1.start();
    }
    public static void timeMillisCountTask(){
        Thread t2 = new Thread(()->{
            long begin = System.currentTimeMillis();
            boolean flag = true;
            while(flag) {
              if (System.currentTimeMillis() - begin > 1000){
                  retransmitTask();
                  begin = System.currentTimeMillis();
              }
            }

        });
        t2.start();
    }
    static void removeId(CopyOnWriteArrayList<Integer> objects,int index) {
        int count =0;
        Iterator<Integer> iterator = objects.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == index){
                objects.remove(count);
                break;
            }
            count++;
        }
    }
}
