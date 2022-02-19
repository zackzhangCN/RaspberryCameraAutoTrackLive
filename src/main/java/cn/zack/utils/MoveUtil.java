package cn.zack.utils;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MoveUtil {
    private static final Logger logger = LoggerFactory.getLogger(MoveUtil.class);

    // 获取gpio控制器
    GpioController gpioController = GPIO_Utils.getGpioController();
    // 定义pin, 其中00针脚接入舵机为上下方位180°运动控制, 01针脚为左右方位180°运动控制
    GpioPinDigitalOutput gpio00 = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_00, "00", PinState.LOW);
    GpioPinDigitalOutput gpio01 = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_01, "01", PinState.LOW);

    private int pointX = 0;

    /**
     * 相机向左
     * SG90 脉冲周期为20ms,脉宽0.5ms-2.5ms对应的角度-90到+90，对应的占空比为2.5%-12。
     *
     * @throws InterruptedException
     */
    public void turnLeft() throws InterruptedException {
        logger.info("相机正在向左...");

        // 如果当前在右侧, 应回正
        if (pointX == 1) {
            turnPointX();
            // 如果当前不在右侧(左或者已回正, 开始左转)
        } else {
            for (int i = 0; i < 20; i++) {
                gpio01.high();

                long highStart = System.nanoTime();
                long highEnd;
                do {
                    highEnd = System.nanoTime();
                } while (highEnd - highStart < 50000);

                gpio01.low();

                long lowStart = System.nanoTime();
                long lowEnd;
                do {
                    lowEnd = System.nanoTime();
                } while (lowEnd - lowStart < 1870000);
            }
            pointX = -1;
        }
    }

    /**
     * 相机向右
     *
     * @throws InterruptedException
     */
    public void turnRight() throws InterruptedException {
        logger.info("相机正在向右...");
        // 如果当前在左侧, 应回正
        if (pointX == -1) {
            turnPointX();
            // 如果当前不在左侧(已回正, 或者在右侧, 开始右转)
        } else {
            for (int i = 0; i < 20; i++) {
                gpio01.high();

                long highStart = System.nanoTime();
                long highEnd;
                do {
                    highEnd = System.nanoTime();
                } while (highEnd - highStart < 2100000);

                gpio01.low();

                long lowStart = System.nanoTime();
                long lowEnd;
                do {
                    lowEnd = System.nanoTime();
                } while (lowEnd - lowStart < 1700000);
            }
            pointX = 1;
        }
    }

    /**
     * 左右回正
     *
     * @throws InterruptedException
     */
    public void turnPointX() throws InterruptedException {
        logger.info("相机回正...");
        for (int i = 0; i < 20; i++) {
            gpio01.high();

            long highStart = System.nanoTime();
            long highEnd;
            do {
                highEnd = System.nanoTime();
            } while (highEnd - highStart < 1400000);

            gpio01.low();

            long lowStart = System.nanoTime();
            long lowEnd;
            do {
                lowEnd = System.nanoTime();
            } while (lowEnd - lowStart < 1800000);
        }
        pointX = 0;
    }


    /**
     * 相机向上
     *
     * @throws InterruptedException
     */
    public void turnUp() throws InterruptedException {
        logger.info("相机正在向上...");
        for (int i = 0; i < 20; i++) {
            gpio00.high();

            long highStart = System.nanoTime();
            long highEnd;
            do {
                highEnd = System.nanoTime();
            } while (highEnd - highStart < 50000);

            gpio00.low();

            long lowStart = System.nanoTime();
            long lowEnd;
            do {
                lowEnd = System.nanoTime();
            } while (lowEnd - lowStart < 1860000);
        }
    }

    /**
     * 相机向下
     *
     * @throws InterruptedException
     */
    public void turnDown() throws InterruptedException {
        logger.info("相机正在向下...");
        for (int i = 0; i < 20; i++) {
            gpio00.high();

            long highStart = System.nanoTime();
            long highEnd;
            do {
                highEnd = System.nanoTime();
            } while (highEnd - highStart < 2100000);

            gpio00.low();

            long lowStart = System.nanoTime();
            long lowEnd;
            do {
                lowEnd = System.nanoTime();
            } while (lowEnd - lowStart < 1700000);
        }
    }

}
