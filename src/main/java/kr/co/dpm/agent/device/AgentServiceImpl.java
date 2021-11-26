package kr.co.dpm.agent.device;


import kr.co.dpm.agent.device.util.Cryptogram;
import kr.co.dpm.agent.device.util.DeviceUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.*;

@Service
public class AgentServiceImpl implements AgentService, InitializingBean, Runnable {
    private static final Logger logger = LogManager.getLogger(AgentService.class);
    private File file;
    @Autowired
    private DeviceUtil deviceUtil;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private MeasureRepository measureRepository;

    @Override
    public void afterPropertiesSet() throws Exception {
        sendDevice();
    }

    @Override
    public void run() {
        String fileName = file.getName();
        String fileDirectory = file.getPath().substring(0, file.getPath().length() - fileName.length() - 1);
        String command = "java -cp " + fileDirectory + " " + fileName.substring(0, fileName.length() - 6);  //여기
        logger.debug("-----> 커맨드 : " + command);

        Measure measure = new Measure();
        measure.setDeviceId(deviceUtil.getDevice().getId());

        try {
            long beforeTime = System.currentTimeMillis();
            logger.debug("-----> 결과 : " + deviceUtil.executeCommand(command));
            long afterTime = System.currentTimeMillis();

            long secDiffTime = (afterTime - beforeTime);
            measure.setExecTime(Long.toString(secDiffTime));
            measure.setStatus('Y');

        } catch (Exception e) {
            measure.setExecTime("0");
            measure.setStatus('N');
        }
        logger.debug("-----> 측정 결과 정보 : " + measure);

        try {
            sendMeasure(measure);

            logger.debug("-----> 측정 송신 성공!");

            file.delete();
        } catch (Exception e) {
            e.printStackTrace();

            logger.debug("-----> 측정 송신 실패 혹은 파일 삭제 실패");
        }
    }

    @Override
    public Device executeCommand() {
        Device device = null;

        if ((device = deviceUtil.getDevice()) == null) {
            return deviceUtil.createDevice();
        }

        return device;
    }

    @Override
    public void sendDevice() {
        Device device = executeCommand();


        for (int i = 0; i < 10; i++) {
            try {
                if (deviceRepository.request(device)) {
                    logger.debug("------>  송신 성공!!");
                    break;
                } else {
                    logger.debug("------>  송신 실패");
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.debug("------>  송신 실패");
            }
        }

    }

    @Override
    public File receiveScript(MultipartFile multipartFile, HttpServletRequest request, String id) throws Exception {
        String deviceId = deviceUtil.getDevice().getId();

        Cryptogram cryptogram = new Cryptogram(deviceId);       //복호화
        String decryptionId = null;
        try {
            decryptionId = cryptogram.decrypt(id);
            if (!deviceId.equals(decryptionId)) {
                throw new FileNotFoundException();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new FileNotFoundException();
        }

        String path = request.getSession().getServletContext().getRealPath("/") + "script";

        File directory = new File(path);        //디렉토리 설정
        if (!directory.isDirectory()) {
            directory.mkdir();
        }

        File file = new File(path + File.separator + multipartFile.getOriginalFilename());
        multipartFile.transferTo(file);         //파일 수신

        return file;
    }

    @Override
    public void executeScript(MultipartFile multipartFile, HttpServletRequest request, String id) throws Exception {
        file = receiveScript(multipartFile, request, id);

        Thread thread  = new Thread(this);      //스크립트 수신에 응답을 하기위한 스레드

        thread.start();
    }

    @Override
    public void sendMeasure(Measure measure) throws Exception {
        measureRepository.request(measure);
    }
}
