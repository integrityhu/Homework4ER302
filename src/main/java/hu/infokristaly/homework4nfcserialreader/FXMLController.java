package hu.infokristaly.homework4nfcserialreader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortException;
import jssc.SerialPortList;

public class FXMLController implements Initializable, jssc.SerialPortEventListener {

    @FXML
    private ComboBox serialPortList;

    @FXML
    private TextArea logArea;

    @FXML
    private Button connectButton;

    @FXML
    private Button disconnectButton;

    @FXML
    private Button beepButton;

    @FXML
    private Button readCardIdButton;

    private SerialPort serialPort;
    
    private static final String newLine = System.getProperty("line.separator");

    private byte[] typeBytes;

    private byte[] buildCommand(byte[] cmd, byte[] data) {
        byte[] result = {};
        short length = (short) (2 + 1 + 2 + data.length); //HEADER {0xaa, 0xbb} + {0x00, LENGTH_IN_BYTES} + RESERVER {0xff, 0xff} + DATALENGTH
        log("length: " + length);

        ByteArrayOutputStream bodyRaw = new ByteArrayOutputStream();
        ByteArrayOutputStream msgRaw = new ByteArrayOutputStream();
        try {
            bodyRaw.write(ER302Driver.RESERVED);
            bodyRaw.write(cmd);
            bodyRaw.write(data);
            byte crc = ER302Driver.crc(bodyRaw.toByteArray());
            bodyRaw.write(crc);

            msgRaw.write(ER302Driver.HEADER);
            msgRaw.write(ER302Driver.shortToByteArray(length, false));
            msgRaw.write(bodyRaw.toByteArray());

            log("command[" + ER302Driver.byteArrayToHexString(msgRaw.toByteArray()) + "]");
            result = msgRaw.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
            log(ex.getMessage());
        }
        return result;
    }

    private byte[] beep(byte msec) {
        byte[] data = {msec};
        byte[] result = buildCommand(ER302Driver.CMD_BEEP, data);
        return result;
    }

    private byte[] mifareRequest() {
        byte[] data = {0x52};
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_REQUEST, data);
        return result;
    }

    private byte[] mifareAnticolision() {
        byte[] data = {0x04};
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_ANTICOLISION, data);
        return result;
    }

    private byte[] readFirmware() {
        byte[] data = {};
        byte[] result = buildCommand(ER302Driver.CMD_READ_FW_VERSION, data);
        return result;
    }

    private byte[] mifareULSelect() {
        byte[] data = {};
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_UL_SELECT, data);
        return result;
    }

    private byte[] mifareSelect() {
        byte[] data = {};
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_SELECT, data);
        return result;
    }

    @FXML
    private void handleConnectButtonAction(ActionEvent event) {
        connentSerial();
    }

    @FXML
    private void handleBeepButtonAction(ActionEvent event) {
        byte[] beepMsg = beep((byte) 100);
        try {
            serialPort.writeBytes(beepMsg);
        } catch (SerialPortException ex) {
            Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
            log(ex.getMessage());
        }
    }

    @FXML
    private void handleCardIdButtonAction(ActionEvent event) {
        try {
            byte[] readFirmwareMsg = readFirmware();
            log("Firmware version:");
            serialPort.writeBytes(readFirmwareMsg);
            Thread.sleep(500L);
            log("MiFare request:");
            byte[] mifareRequestMsg = mifareRequest();
            serialPort.writeBytes(mifareRequestMsg);
        } catch (SerialPortException | InterruptedException ex) {
            Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
            log(ex.getMessage());
        }
    }

    @FXML
    private void handleDisconnectButtonAction(ActionEvent event) {
        closeSerialPort();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        String[] portNames = SerialPortList.getPortNames();
        for (int i = 0; i < portNames.length; i++) {
            serialPortList.getItems().add(new String(portNames[i]));
        }
    }

    @Override
    protected void finalize() throws Throwable {
        closeSerialPort();
        super.finalize();
    }

    public void serialEvent(SerialPortEvent event) {
        if (event.isRXCHAR()) {
            int count = event.getEventValue();
            if (count > 0) {
                try {
                    byte buffer[] = serialPort.readBytes(count);
                    String input = ER302Driver.byteArrayToHexString(buffer); //"distance:50 mm" 
                    log("received[" + input + "]");
                    ER302Driver.ReceivedStruct result = ER302Driver.decodeReceivedData(buffer);
                    if (Arrays.equals(result.cmd, ER302Driver.CMD_READ_FW_VERSION)) {
                        log("Firmware version:" + new String(result.data));
                    } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_ANTICOLISION)) {
                        log("MiFARE Classic UID:" + ER302Driver.byteArrayToHexString(result.data));
                        if (Arrays.equals(typeBytes, ER302Driver.TYPE_MIFARE_1K)) {
                            log("CardType: MiFARE Classic 1K");
                            byte[] mifareSelectMsg = mifareSelect();
                            serialPort.writeBytes(mifareSelectMsg);
                        } else if (Arrays.equals(typeBytes, ER302Driver.TYPE_MIFARE_UL)) {
                            log("CardType: MiFARE UltraLight");
                            byte[] mifareULSelectMsg = mifareULSelect();
                            serialPort.writeBytes(mifareULSelectMsg);
                        }
                    } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_REQUEST)) {
                        log("MiFARE Request result:" + ER302Driver.byteArrayToHexString(result.data));
                        typeBytes = Arrays.copyOf(result.data, 2);
                        byte[] mifareAnticolisionMsg = mifareAnticolision();
                        serialPort.writeBytes(mifareAnticolisionMsg);
                    } else {
                        log("Data:" + ER302Driver.byteArrayToHexString(result.data));
                    }
                } catch (SerialPortException ex) {
                    Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
                    log(ex.getMessage());
                }
            }
        }
    }

    void closeSerialPort() {
        if ((serialPort != null) && serialPort.isOpened()) {
            try {
                serialPort.closePort();
                updateButtons(false);
                log("Disconnected.");
            } catch (SerialPortException ex) {
                Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
                log(ex.getMessage());
            }
        }

    }

    private void connentSerial() {
        if (serialPortList.getValue() != null) {
            if (serialPort == null) {
                serialPort = new SerialPort(serialPortList.getValue().toString());
            }
            if (!serialPort.isOpened()) {
                try {
                    //Open port
                    serialPort.openPort();
                    //We expose the settings. You can also use this line - serialPort.setParams(9600, 8, 1, 0);
                    serialPort.setParams(SerialPort.BAUDRATE_115200,
                            SerialPort.DATABITS_8,
                            SerialPort.STOPBITS_1,
                            SerialPort.PARITY_NONE);
                    int mask = SerialPort.MASK_RXCHAR;
                    serialPort.setEventsMask(mask);
                    serialPort.addEventListener(this);
                    updateButtons(true);
                    log("Connected.");
                } catch (SerialPortException ex) {
                    Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
                    log(ex.getMessage());
                    updateButtons(false);
                }
            }
        }
    }

    private void log(String msg) {
        System.out.println(msg);
        logArea.appendText(msg + newLine);
    }

    private void updateButtons(boolean connected) {
        connectButton.setDisable(connected);
        disconnectButton.setDisable(!connected);
        readCardIdButton.setDisable(!connected);
        beepButton.setDisable(!connected);
    }
}
