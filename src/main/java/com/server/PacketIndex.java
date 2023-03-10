package com.server;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @version 1.0
 * @Author:qiu
 * @Description 文件索引 给定一个order id 返回文件内容的起始位置
 * @Date 15:16 2023/3/3
 **/
public class PacketIndex {

     String filePath;
     String fileName;
     int packetLength;
     long start;
     long end;
     long fileLength;
     RandomAccessFile raf = null;
     int lastPacketSize = 0;
     int lastPacketOrder = 0 ;

    public PacketIndex(String filePath,String fileName,int packetLength) throws IOException {
        this.packetLength = packetLength;
        setFileLength(filePath,fileName);
    }

    private void setFileLength(String filePath,String fileName) throws IOException {
        this.filePath = filePath;
        this.fileName = fileName;
        raf = new RandomAccessFile(filePath+fileName,"r");
        fileLength = raf.length();
        //得到文件最后一个包的大小
        lastPacketSize = fileLength % packetLength == 0
                ? packetLength : (int) (fileLength % packetLength);
        //得到文件最后一个包的顺序id
        lastPacketOrder = fileLength % packetLength == 0
                ? (int) (fileLength / packetLength) : (int) (fileLength / packetLength) + 1;
    }


    /**
     * 返回指定一个包 在文件中的起始读写位置
     * @param order
     * @return
     */
    public long[] getSeek(int order){
        long[] startWithEnd = new long[2];
        if (order != lastPacketOrder){
        start =  (order - 1) * packetLength;
        end = start + packetLength;
        startWithEnd[0] = start;
        startWithEnd[1] = end;
        }
        else {
            startWithEnd[0] = (order - 1) * packetLength;
            startWithEnd[1] = startWithEnd[0] + lastPacketSize;
        }
        return startWithEnd;
    }

    public void fileClose() throws IOException {
        if (raf != null){
            raf.close();
        }
    }

}
