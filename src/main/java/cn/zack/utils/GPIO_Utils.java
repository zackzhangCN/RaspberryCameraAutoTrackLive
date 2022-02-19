package cn.zack.utils;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;

/**
 * @author cn.zack
 * GPIO控制器
 */
public class GPIO_Utils {

    private static GpioController gpioController;

    /**
     * 双锁检查 创建全局唯一GPIO控制器, 防止多线程操作GPIO损坏主板
     *
     * @return
     */
    public static GpioController getGpioController() {
        if (gpioController == null) {
            synchronized (GPIO_Utils.class) {
                if (gpioController == null) {
                    gpioController = GpioFactory.getInstance();
                }
            }
        }
        return gpioController;
    }
}
