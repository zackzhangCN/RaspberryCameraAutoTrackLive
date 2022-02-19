package cn.zack.camera;

import cn.zack.utils.MoveUtil;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class VideoService {

    private static final Logger logger = LoggerFactory.getLogger(VideoService.class);

    /**
     * 音频设备驱动号
     */
    @Value("${camera.audio.device}")
    private int audioDevice;

    /**
     * 相机驱动号
     */
    @Value("${camera.video.device}")
    private int cameraDevice;

    /**
     * 录制帧率, 最低25(低于25帧会闪屏)
     */
    @Value("${camera.video.rate}")
    private int rate;

    /**
     * 录制画面宽度
     */
    @Value("${camera.video.width}")
    private int width;

    /**
     * 录制画面高度
     */
    @Value("${camera.video.height}")
    private int height;

    /**
     * 画面中心矩形的四条边界线
     * 左右两条边界线为X轴坐标值
     * 上下两条边界线为Y轴坐标值
     */
    @Value("${camera.video.center.leftLine}")
    private int leftLine;
    @Value("${camera.video.center.rightLine}")
    private int rightLine;
    @Value("${camera.video.center.topLine}")
    private int topLine;
    @Value("${camera.video.center.downLine}")
    private int downLine;

    /**
     * 画面中心点坐标
     */
    @Value("${camera.video.center.pointX}")
    private int pointX;
    @Value("${camera.video.center.pointY}")
    private int pointY;

    /**
     * 帧截图临时保存位置
     */
    @Value("${camera.screen.path}")
    private String screenPath;

    /**
     * rtmp协议推流地址
     */
    @Value("${rtmp.push.url}")
    private String rtmpUrl;

    /**
     * 通知rtmp服务端开启录制接口
     */
    @Value("${rtmp.record.start}")
    private String recordStartUrl;

    /**
     * 通知rtmp服务端开启录制接口
     */
    @Value("${rtmp.history.prefix}")
    private String historyLivePrefix;

    /**
     * 通知rtmp服务端停止录制接口
     */
    @Value("${rtmp.record.stop}")
    private String recordStoptUrl;

    /**
     * 注入restTemplate, 用于通知rtmp服务端开始或者停止录制
     */
    @Autowired
    private RestTemplate restTemplate;

    /**
     * 注入邮件发送器
     */
    @Autowired
    private JavaMailSender javaMailSender;

    /**
     * 舵机运动控制
     */
    @Autowired
    private MoveUtil moveUtil;

    /**
     * rtmp服务端的录制状态
     */
    private boolean recordStatus = false;

    /**
     * 本次录制最后一次检测到人脸的时间, 毫秒值
     */
    private long recordLastFaceTime = 0;

    /**
     * 推送/录制本机的音/视频(Webcam/Microphone)到流媒体服务器(Stream media server)
     */
    @PostConstruct
    public void recordWebcamAndMicrophone() throws FrameGrabber.Exception {
        /**
         * FrameGrabber 类包含：OpenCVFrameGrabber
         * (opencv_videoio),C1394FrameGrabber, FlyCaptureFrameGrabber,
         * OpenKinectFrameGrabber,PS3EyeFrameGrabber,VideoInputFrameGrabber,
         * FFmpegFrameGrabber.
         */
        FrameGrabber grabber = new OpenCVFrameGrabber(cameraDevice);
        grabber.setImageWidth(width);
        grabber.setImageHeight(height);
        logger.info("开始获取摄像头...");
        try {
            grabber.start();
            logger.info("开启摄像头成功...");
        } catch (Exception e) {
            try {
                logger.error("摄像头开启失败, 尝试重启摄像头...");
                grabber.restart();
            } catch (Exception ex) {
                logger.error("重启摄像头失败...");
            }
        }

        /**
         * FFmpegFrameRecorder(String filename, int imageWidth, int imageHeight,
         * int audioChannels) fileName可以是本地文件（会自动创建），也可以是RTMP路径（发布到流媒体服务器）
         * imageWidth = width （为捕获器设置宽） imageHeight = height （为捕获器设置高）
         * audioChannels = 2（立体声）；1（单声道）；0（无音频）
         */
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(rtmpUrl, width, height, 2);
        recorder.setInterleaved(true);

        /**
         * 该参数用于降低延迟 参考FFMPEG官方文档：https://trac.ffmpeg.org/wiki/StreamingGuide
         * 官方原文参考：ffmpeg -f dshow -i video="Virtual-Camera" -vcodec libx264
         * -tune zerolatency -b 900k -f mpegts udp://10.1.0.102:1234
         */
        recorder.setVideoOption("tune", "zerolatency");

        /**
         * 权衡quality(视频质量)和encode speed(编码速度) values(值)：
         * ultrafast(终极快),superfast(超级快), veryfast(非常快), faster(很快), fast(快),
         * medium(中等), slow(慢), slower(很慢), veryslow(非常慢)
         * ultrafast(终极快)提供最少的压缩（低编码器CPU）和最大的视频流大小；而veryslow(非常慢)提供最佳的压缩（高编码器CPU）的同时降低视频流的大小
         * 参考：https://trac.ffmpeg.org/wiki/Encode/H.264 官方原文参考：-preset ultrafast
         * as the name implies provides for the fastest possible encoding. If
         * some tradeoff between quality and encode speed, go for the speed.
         * This might be needed if you are going to be transcoding multiple
         * streams on one machine.
         */
        recorder.setVideoOption("preset", "fast");

        /**
         * 参考转流命令: ffmpeg
         * -i'udp://localhost:5000?fifo_size=1000000&overrun_nonfatal=1' -crf 30
         * -preset ultrafast -acodec aac -strict experimental -ar 44100 -ac
         * 2-b:a 96k -vcodec libx264 -r 25 -b:v 500k -f flv 'rtmp://<wowza
         * serverIP>/live/cam0' -crf 30
         * -设置内容速率因子,这是一个x264的动态比特率参数，它能够在复杂场景下(使用不同比特率，即可变比特率, 数值越大, 画质越差, 一般18认为视觉无损)保持视频质量；
         * 可以设置更低的质量(quality)和比特率(bit rate),参考Encode/H.264 -preset ultrafast
         * -参考上面preset参数，与视频压缩率(视频大小)和速度有关,需要根据情况平衡两大点：压缩率(视频大小)，编/解码速度 -acodec
         * aac -设置音频编/解码器 (内部AAC编码) -strict experimental
         * -允许使用一些实验的编解码器(比如上面的内部AAC属于实验编解码器) -ar 44100 设置音频采样率(audio sample
         * rate) -ac 2 指定双通道音频(即立体声) -b:a 96k 设置音频比特率(bit rate) -vcodec libx264
         * 设置视频编解码器(codec) -r 25 -设置帧率(frame rate) -b:v 500k -设置视频比特率(bit
         * rate),比特率越高视频越清晰,视频体积也会变大,需要根据实际选择合理范围 -f flv
         * -提供输出流封装格式(rtmp协议只支持flv封装格式) 'rtmp://<FMS server
         * IP>/live/cam0'-流媒体服务器地址
         */
        recorder.setVideoOption("crf", "18");

        /**
         * 使用硬编解码
         * 参考ffmpeg -f alsa -ar 16000 -ac 1 -i hw:1 -f video4linux2 -s 640x480 -r 25 -i /dev/video0 -c:v h264_omx -f flv "rtmp地址"
         * -f  指定输入或输出文件格式
         * alsa 高级Linux声音架构的简称
         * video4linux2 为linux中视频设备的内核驱动
         * -ar 16000 指定音频采样率为16kHz
         * -ac 1 指定音频声数
         * -i hw:1 指定输入来源
         * -s 640x480 指定帧大小（不指定就为原画尺寸）
         * -r 25 指定帧率fps
         * -c:v h264_omx 指定用h264_omx解码（树莓派的硬件解码，速度快得多）
         */
//        recorder.setVideoOption("c:v", "h264_omx");
        // h264编/解码器
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        // 4000 kb/s, 1080P视频的合理比特率范围
        recorder.setVideoBitrate(2000000);
        // 封装格式flv
        recorder.setFormat("flv");
        // 视频帧率(保证视频质量的情况下最低25，低于25会出现闪屏)
        recorder.setFrameRate(rate);
        /**
         * 视频帧通常分为B,P,I
         * 其中I为关键帧, 实际上是一张完整的静态图像
         * B帧和P帧是用来记录的运动矢量等非图像数据, 需要依赖I帧才能够解码出完整图像(有损图像)
         * 其中大量使用B帧可以提高压缩率, 但是会消耗更多硬件性能(CPU或GPU编码)
         * 大部分情况下都以I帧和大量P帧为主
         * 设置关键帧间隔, 间隔越短则拉流端显示首帧画面越快(B,P帧需要I帧才能解码), 但同时增加了传输I帧的数据量
         */
        recorder.setGopSize(rate);
        // 不可变(固定)音频比特率
        recorder.setAudioOption("crf", "0");
        // 最高质量
        recorder.setAudioQuality(0);
        // 音频比特率
        recorder.setAudioBitrate(192000);
        // 音频采样率
        recorder.setSampleRate(44100);
        // 双通道(立体声)
        recorder.setAudioChannels(2);
        // 音频编/解码器
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);

        try {
            // 开启录制器
            logger.info("开始录制...");
            recorder.start();
        } catch (Exception e) {
            try {
                logger.error("录制失败，尝试重启录制...");
                recorder.stop();
                recorder.start();
            } catch (Exception ex) {
                logger.error("重启录制失败...");
            }
        }
        // 异步线程捕获音频
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        new Thread(() -> {
            /**
             * 设置音频编码器 最好是系统支持的格式，否则getLine() 会发生错误
             * 采样率:44.1k;采样率位数:16位;立体声(stereo);是否签名;true:
             * big-endian字节顺序,false:little-endian字节顺序(详见:ByteOrder类)
             */
            AudioFormat audioFormat = new AudioFormat(44100.0F, 16, 2, true, false);
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            try {
                // 打开并开始捕获音频
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
                line.open(audioFormat);
                line.start();
                // 获得当前音频采样率
                int sampleRate = (int) audioFormat.getSampleRate();
                // 获取当前音频通道数量
                int numChannels = audioFormat.getChannels();
                // 初始化音频缓冲区(size是音频采样率*通道数)
                int audioBufferSize = sampleRate * numChannels;
                byte[] audioBytes = new byte[audioBufferSize];

                // 使用延时线程逐帧写入音频, 延时时间为1秒/帧率
                exec.scheduleAtFixedRate(() -> {
                    try {
                        // 非阻塞方式读取
                        int nBytesRead = line.read(audioBytes, 0, line.available());
                        // 因为我们设置的是16位音频格式,所以需要将byte[]转成short[]
                        int nSamplesRead = nBytesRead / 2;
                        short[] samples = new short[nSamplesRead];
                        /**
                         * ByteBuffer.wrap(audioBytes)-将byte[]数组包装到缓冲区
                         * ByteBuffer.order(ByteOrder)-按little-endian修改字节顺序，解码器定义的
                         * ByteBuffer.asShortBuffer()-创建一个新的short[]缓冲区
                         * ShortBuffer.get(samples)-将缓冲区里short数据传输到short[]
                         */
                        ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
                        // 将short[]包装到ShortBuffer
                        ShortBuffer sBuff = ShortBuffer.wrap(samples, 0, nSamplesRead);
                        // 按通道录制shortBuffer
                        recorder.recordSamples(sampleRate, numChannels, sBuff);
                    } catch (Exception e) {
                        logger.error("读取音频缓冲流失败...");
                    }
                }, 0, (long) 800 / rate, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                logger.error("获取音频失败...");
            }
        }).start();

        // javaCV提供了优化非常好的硬件加速组件来帮助显示我们抓取的摄像头视频
        CanvasFrame cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());

        /**
         * 读取opencv人脸训练模型
         * 如果读取不到, 则error: (-215:Assertion failed) !empty() in function 'cv::CascadeClassifier::detectMultiScale'
         * 注意, 209错误可能为模型文件格式错误或者解析出错
         */
        String path = Thread.currentThread().getContextClassLoader().getResource("haarcascade_frontalface_alt.xml").getPath();
        if (path.contains("BOOT-INF")) {
            path = path.replace("!", "").split("BOOT-INF/")[1];
        } else {
            path = path.substring(1);
        }
        logger.info(path);
        // 加载模型配置
        CascadeClassifier cascade = new CascadeClassifier(path);

        // 人脸框颜色
        Scalar faceScalar = new Scalar(0, 255, 0, 1);
        // 转换器，用于Frame/Mat/IplImage相互转换
        OpenCVFrameConverter.ToIplImage converter = new OpenCVFrameConverter.ToIplImage();
        // 时间戳水印位置
        Point timePoint = new Point(920, 50);
        // 时间戳水印颜色
        Scalar timeScalar = new Scalar(0, 255, 255, 0);
        // 时间戳格式
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // 执行抓取（capture）过程
        Frame capturedFrame;
        long videoTS;
        long startTime = System.currentTimeMillis();
        while ((capturedFrame = grabber.grab()) != null) {
            // 创建一个 timestamp用来同步音频
            videoTS = 1000 * (System.currentTimeMillis() - startTime);
            //检查偏移量
            recorder.setTimestamp(videoTS);
            // 获取当前帧彩色图像
            Mat mat = (Mat) capturedFrame.opaque;
            // 存放灰度图
            Mat grayImg = new Mat();
            /**
             * 摄像头获取的是彩色图像转灰度
             * 如果要获取摄像头灰度图，可以直接对FrameGrabber进行设置grabber.setImageMode(ImageMode.GRAY);，grabber.grab()获取的都是灰度图
             */
            cvtColor(mat, grayImg, COLOR_BGRA2GRAY);
            // 均衡化直方图
            equalizeHist(grayImg, grayImg);
            // 检测到人脸
            RectVector faces = new RectVector();
            // 给人脸绘制矩形
            cascade.detectMultiScale(grayImg, faces);
            // 收集当前帧中的矩形
            int[][] rectangles = new int[(int) faces.size()][2];
            // 遍历人脸
            for (int i = 0; i < faces.size(); i++) {
                Rect face_i = faces.get(i);
                //绘制人脸矩形区域，scalar色彩顺序：BGR(蓝绿红)
                rectangle(mat, face_i, faceScalar);
                // 计算此矩形中心点坐标
                rectangles[i][0] = face_i.x() + (face_i.width() / 2);
                rectangles[i][1] = face_i.y() + (face_i.height() / 2);
            }
            // 如果当前帧画面中检测到人脸
            if (rectangles.length > 0) {
                // 最后一次检测到人脸的时间重置为当前时间
                recordLastFaceTime = System.currentTimeMillis();
                // 计算当前帧中矩形的总体偏向方位, 开启目标追踪
                try {
                    getPosition(rectangles);
                } catch (InterruptedException e) {
                    logger.info("运动追踪发生异常");
                }
                // 如果当前不在录制中
                if (!recordStatus) {
                    // 通知录制线程开始录制
                    recordStart();
                    // 同时将当前帧的图片临时保存
                    opencv_imgcodecs.imwrite(screenPath, mat);
                }
            } else {
                // 如果当前正在录制中, 并且连续5秒检测不到人脸, 停止录制
                if (recordStatus && System.currentTimeMillis() - recordLastFaceTime > 10000) {
                    recordStop();
                    // 舵机回正
                    try {
                        moveUtil.turnPointX();
                        // todo 上下回正待完成
                    } catch (InterruptedException e) {
                        logger.info("舵机回正发生异常");
                    }
                }
            }

            // 开启本地预览
            if (cFrame.isVisible()) {
                //本机预览要发送的帧
                cFrame.showImage(capturedFrame);
            }

            try {
                // 每一帧添加时间戳水印
                opencv_imgproc.putText(mat,
                        format.format(new Date()),
                        timePoint,
                        opencv_imgproc.CV_FONT_ITALIC,
                        0.8,
                        timeScalar,
                        2,
                        20,
                        false);
                // 发送帧
                recorder.record(capturedFrame);
            } catch (Exception e) {
                logger.error("录制帧发生异常");
            }
        }
        // 本地预览
        cFrame.dispose();
        try {
            recorder.stop();
            recorder.release();
        } catch (FrameRecorder.Exception e) {
            logger.error("关闭录制器失败");
        }
        try {
            grabber.stop();
        } catch (Exception e) {
            logger.error("关闭摄像头失败");
        }
    }

    /**
     * 计算画面中多个矩形总体偏向画面的方位
     *
     * @param rectangles 二维矩形数组, 第一维保存矩形的宽, 第二维保存矩形的高
     * @return 偏向方位, 0为中央, 1为左上, -1为左下, 2为右上, -2为右下
     */
    public void getPosition(int[][] rectangles) throws InterruptedException {
        // 初始化多个矩形整体中心点坐标
        int centerX = 0;
        int centerY = 0;
        for (int i = 0; i < rectangles.length; i++) {
            // 取当前点的坐标
            int thisPointX = rectangles[i][0];
            int thisPointY = rectangles[i][1];
            centerX += thisPointX;
            centerY += thisPointY;
        }
        // 得到中心点坐标
        centerX = centerX / rectangles.length;
        centerY = centerY / rectangles.length;

        // 位于中心区域
        if (centerX >= leftLine && centerX <= rightLine && centerY >= topLine && centerY <= downLine) {
            // 中心区域, 无需不追踪, 暂时不作处理
        } else {
            // 不在中心区域, 继续判定具体方位
            // 偏左
            if (centerX < pointX) {
                moveUtil.turnLeft();
                // 继续判定左上或者左下
                if (centerY < pointY) {
                    moveUtil.turnUp();
                } else {
                    moveUtil.turnDown();
                }
            } else {
                moveUtil.turnRight();
                // 继续判定右上或者右下
                if (centerY < pointY) {
                    moveUtil.turnUp();
                } else {
                    moveUtil.turnDown();
                }
            }
        }
    }

    /**
     * 异步通知流媒体服务端开始录制
     */
    public void recordStart() {
        // 如果当前是非录制状态, 通知rtmp服务端开始录制
        if (!recordStatus) {
            // 录制状态改为开启
            recordStatus = true;
            new Thread(() -> {
                String startResponse = restTemplate.getForObject(recordStartUrl, String.class);
                logger.info("通知rtmp服务端开始录制, 响应报文: {}", startResponse);
                // 发邮件通知
                if (startResponse != null) {
                    sendMail(historyLivePrefix + startResponse.substring(11));
                }
            }).start();
        }
    }

    /**
     * 异步通知流媒体服务端停止录制
     */
    public void recordStop() {
        // 如果当前是录制状态, 通知rtmp服务端停止录制
        if (recordStatus) {
            // 录制状态改为停止
            recordStatus = false;
            new Thread(() -> {
                String startResponse = restTemplate.getForObject(recordStoptUrl, String.class);
                logger.info("通知rtmp服务端停止录制, 响应报文: {}", startResponse);
            }).start();
        }
    }

    /**
     * 发送邮件
     */
    public void sendMail(String mailText) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper messageHelper;
        try {
            messageHelper = new MimeMessageHelper(mimeMessage, true);
            // 设置邮件主题
            messageHelper.setSubject("检测到人脸");
            // 设置邮件发送者，这个跟application.yml中设置的要一致
            messageHelper.setFrom("xxxxxxxxx@qq.com");
            // 设置邮件接收者，可以有多个接收者，中间用逗号隔开，以下类似
            messageHelper.setTo("xxxxxxxxx@qq.com");
            // 设置邮件抄送人，可以有多个抄送人
            messageHelper.setCc("xxxxxxxxx@qq.com");
            // 设置邮件发送日期
            messageHelper.setSentDate(new Date());
            // 设置邮件的正文
            messageHelper.setText(mailText);
            // 设置附件
            FileSystemResource file = new FileSystemResource(screenPath);
            messageHelper.addAttachment("temp.jpg", file);
            // 发送邮件
            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
