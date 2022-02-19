### 基于Raspberry开发板构建监控平台
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
- 客户端系统采用Raspbian, 服务端系统采用Ubuntu
- SpringBoot开源框架
- JavaCV开源框架
- Pi4j开源框架
- Nginx开源框架, 以及nginx-rtmp模块提供流媒体支持
- Frp开源软件, 提供局域网对公网穿透能力
- 阿里云AAAA域名解析以及自建IPv6DDNS对IPV6网络支持
- 参考了motion软件的一些实现

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