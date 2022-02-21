### 基于Raspberry开发板构建监控平台

---

##### 已实现功能: 
- 音视频直播  ✔
- 人脸识别  ✔
- 运动追踪  ✔
- 告警通知  ✔

##### 待实现功能:
- 双向通话  ✖

---

##### 硬件相关: 
- 主板采用Raspberry 4B开发板
- 运动控制采用SG90舵机(或者5线4相步进电机), 支持2自由度运动
- 相机采用CSI相机模块(也可采用USB相机模块)
- 红外成像采用850nm不可见红外光以及光敏二极管切换红外模式

---
##### 软件相关:
- 推流端系统采用Raspbian, 服务端系统采用Ubuntu, 客户端可基于flv.js在线播放或者支持rtmp协议的播放器
- SpringBoot开源框架
- JavaCV开源框架
- Pi4j开源框架
- Nginx开源框架, 以及nginx-rtmp模块提供流媒体支持
- Frp开源软件, 提供局域网对公网穿透能力
- 阿里云AAAA域名解析以及自建IPv6DDNS对IPV6网络支持
- 参考了motion软件的一些实现

---
##### 实现原理
- 推流端运行在Raspberry 4B开发板, 基于javacv实时捕获相机和麦克风数据混合为视频流, 使用opencv官方训练模型对每一帧图像进行分析, 判定是否存在人脸信息(通知流媒体服务器开始/停止录制)以及目标偏离画面中心的方位(通过GPIO接口控制舵机转向目标方位), 同时通过ffmpeg推流到流媒体服务端
- 流媒体服务端运行在X86主机(或者ECS), 基于nginx以及nginx-rtmp模块搭建流媒体服务器, 同时暴露直播开始/停止录制接口, 此处不进行转码
- 播放端运行在web端(基于flv.js的播放器)或者app(支持rtmp协议)
- 关于双向通话, 需要播放端支持音频录制(暂未完成), 将录制的实时音频推流到流媒体服务器另一个直播流, 同时通知原推流端(Raspberry 4B)拉流播放

---
#### 启动与部署
由于JavaCV基于JavaCPP封装了OpenCV在各个平台的实现以及对应的平台库, 与OpenCV一样, 在不同的平台需要指定不同编译环境
在X86平台编译: 
```shell
mvn clean install -DskipTests
```
在arm平台编译:
```shell
mvn clean install -Dplatform.name=linux-arm
```

编译后的jar包可直接运行
```shell
nohup java -jar XXX.jar &
```

---
感谢以上开源社区做出的贡献!!!
