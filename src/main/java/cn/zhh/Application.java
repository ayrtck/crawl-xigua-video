import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.Keys;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 主类
 * 修改“小视频首页”和“存放目录”之后运行main函数即可
 *
 * @author Zhou Huanghua
 */
@SuppressWarnings("all")
public class Application {

    /**
     * 小视频首页，按需修改 https://www.ixigua.com/home/3276166340814919/hotsoon/
     */
    private static final String MAIN_PAGE_URL = "https://www.ixigua.com/home/6871015347/video";

    /**
     * 存放目录，提前创建好路径，按需修改
     */
    private static final String FILE_SAVE_DIR = "C:/Users/win10/Desktop/MP4/";

    /**
     * 线程池，按需修改并行数量。实际开发请自定义避免OOM
     */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * 谷歌浏览器参数
     */
    private static final ChromeOptions CHROME_OPTIONS = new ChromeOptions();

    static {
        // 驱动位置
        System.setProperty("webdriver.chrome.driver", "src/main/resources/static/chromedriver.exe");
        // 避免被浏览器检测识别
        CHROME_OPTIONS.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
    }

    /**
     * main函数
     *
     * @param args 运行参数
     * @throws InterruptedException 睡眠中断异常
     */
    public static void main(String[] args) throws InterruptedException {
        // 获取小视频列表的div元素，批量处理
        Document mainDoc = Jsoup.parse(getMainPageSource());
        // System.out.println(mainDoc);
        // 旧的：BU-CardB UserDetail__main__list-item
        // 2020年4月2日 西瓜视频个人首页TA的视频Tab HorizontalFeedCard
        Elements divItems = mainDoc.select("div[class=\"HorizontalFeedCard\"]");
        System.out.println("总共提取的视频：" + divItems.size());
        // 这里使用CountDownLatch关闭线程池，只是避免执行完一直没退出
        CountDownLatch countDownLatch = new CountDownLatch(divItems.size());
        divItems.forEach(item ->
                EXECUTOR.execute(() -> {
                    try {
                        Application.handleItemByVideoTab(item);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    countDownLatch.countDown();
                })
        );
        countDownLatch.await();
        EXECUTOR.shutdown();
        System.exit(0);
    }

    /**
     * 获取首页内容
     *
     * @return 首页内容
     * @throws InterruptedException 睡眠中断异常
     */
    private static String getMainPageSource() throws InterruptedException {
        ChromeDriver driver = new ChromeDriver(CHROME_OPTIONS);
        try {
            driver.get(MAIN_PAGE_URL);
            long waitTime = Double.valueOf(Math.max(3, Math.random() * 5) * 1000).longValue();
            TimeUnit.MILLISECONDS.sleep(waitTime);
            long timeout = 60_000;
            // 循环下拉，直到全部加载完成或者超时
            do {
                new Actions(driver).sendKeys(Keys.END).perform();
                TimeUnit.MILLISECONDS.sleep(waitTime);
                timeout -= waitTime;
            } while (!driver.getPageSource().contains("已经到底部，没有新的内容啦")
                    && timeout > 0);
            return driver.getPageSource();
        } finally {
            driver.close();
        }
    }

    /**
     * 处理每个视频
     *
     * @param div
     * @throws Exception
     * @author ayrtck
     */
    private static void handleItemByVideoTab(Element div) throws Exception {
        String href = div.getElementsByTag("a").first().attr("href");
        String title = div.getElementsByTag("a").first().attr("title");
        String src = getVideoUrl("https://www.ixigua.com/embed?group_id=" + href.replaceAll("/", "").substring(1));
        if (src.startsWith("//")) {
            // 1.下载网络文件
            int byteRead;
            URL url = new URL("https:" + src);
            //2.获取链接
            URLConnection conn = url.openConnection();
            //3.输入流
            InputStream inStream = conn.getInputStream();
            //3.写入文件
            FileOutputStream fs = new FileOutputStream(FILE_SAVE_DIR + title + ".mp4");
            byte[] buffer = new byte[1024];
            while ((byteRead = inStream.read(buffer)) != -1) {
                fs.write(buffer, 0, byteRead);
            }
            inStream.close();
            fs.close();
        } else {
            System.out.println("无法解析的src：[" + src + "]");
        }
    }

    /**
     * 处理每个小视频
     *
     * @param div 小视频div标签元素
     * @throws Exception 各种异常
     */
    private static void handleItemByHotsoonTab(Element div) throws Exception {
        String href = div.getElementsByTag("a").first().attr("href");
        String src = getVideoUrl("https://www.ixigua.com" + href);
        // 有些blob开头的（可能还有其它）暂不处理
        if (src.startsWith("//")) {
            Connection.Response response = Jsoup.connect("https:" + src)
                    // 解决org.jsoup.UnsupportedMimeTypeException: Unhandled content type. Must be text/*, application/xml, or application/xhtml+xml. Mimetype=video/mp4, URL=
                    .ignoreContentType(true)
                    // The default maximum is 1MB.
                    .maxBodySize(100 * 1024 * 1024)
                    .execute();
            Files.write(Paths.get(FILE_SAVE_DIR, href.replaceAll("/", "").substring(1) + ".mp4"), response.bodyAsBytes());
        } else {
            System.out.println("无法解析的src：[" + src + "]");
        }
    }

    /**
     * 获取视频实际链接
     *
     * @param itemUrl 视频详情页
     * @return 小视频实际链接
     * @throws InterruptedException 睡眠中断异常
     */
    private static String getVideoUrl(String itemUrl) throws InterruptedException {
        ChromeDriver driver = new ChromeDriver(CHROME_OPTIONS);
        try {
            driver.get(itemUrl);
            long waitTime = Double.valueOf(Math.max(5, Math.random() * 10) * 1000).longValue();
            long timeout = 60_000;
            Element v;
            /**
             * 循环等待，直到链接出来
             * ※这里可以考虑浏览器驱动自带的显式等待()和隐士等待
             */
            do {
                TimeUnit.MILLISECONDS.sleep(waitTime);
                timeout -= waitTime;
            } while ((Objects.isNull(v = Jsoup.parse(driver.getPageSource()).getElementById("vs"))
                    || Objects.isNull(v = v.getElementsByTag("video").first()))
                    && timeout > 0);

            return v.attr("src");
        } finally {
            driver.close();
        }
    }
}