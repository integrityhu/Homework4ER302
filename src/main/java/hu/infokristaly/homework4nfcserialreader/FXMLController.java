package hu.infokristaly.homework4nfcserialreader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.concurrent.Task;
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

    private byte state = 0;

    private Queue<ER302Driver.CommandStruct> commands = new LinkedList<ER302Driver.CommandStruct>();
    private Map<Integer, ER302Driver.CommandStruct> commandMap = new HashMap<Integer, ER302Driver.CommandStruct>();

    private enum LED {
        RED, BLUE, OFF
    };

    private ER302Driver.CommandStruct lastCommand;

    private SerialPort serialPort;

    private ByteArrayOutputStream bout;

    private static final String newLine = System.getProperty("line.separator");

    private byte[] typeBytes;
    private byte[] cardSerialNo;

    private byte[] buildCommand(byte[] cmd, byte[] data) {
        byte[] result = {};
        short length = (short) (2 + 1 + 2 + data.length); //HEADER {0xaa, 0xbb} + {0x00, LENGTH_IN_BYTES} + RESERVER {0xff, 0xff} + DATALENGTH

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

    private byte[] led(LED color) {
        byte[] data;
        switch(color) {
            case OFF:
                data = new byte[]{0x00};
                break;
            case RED: 
                data = new byte[]{0x02}; //changed for my device from 0x01
                break;
            case BLUE: 
                data = new byte[]{0x01}; //changed for my device from 0x02
                break;
            default: 
                data = new byte[]{0x03}; //both led on, but my device is red only
        }
        byte[] result = buildCommand(ER302Driver.CMD_LED, data);
        return result;
    }

    private byte[] mifareRequest() {
        byte[] data = {0x52};
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_REQUEST, data);
        return result;
    }

    private byte[] readBalance(char sector, char block) {
        byte[] data = {(byte) (sector * 4 + block)};
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_READ_BALANCE, data);
        return result;
    }

    private byte[] auth2(char sector) {
        byte[] key = new byte[6];
        Arrays.fill(key, (byte) 0xFF);
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.put((byte) 0x60);
        bb.put(((byte) (sector * 4)));
        bb.put(key);
        byte[] data = bb.array();
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_AUTH2, data);
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

    private byte[] mifareSelect(byte[] select) {
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_SELECT, select);
        return result;
    }

    private byte[] incBalance(char sector, char block, int i) {
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put(((byte) (sector * 4 + block)));
        byte[] intInc = ER302Driver.intToByteArray(i, false);
        bb.put(intInc);
        byte[] data = bb.array();
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_INCREMENT, data);
        return result;
    }

    private byte[] decBalance(char sector, char block, int i) {
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put(((byte) (sector * 4 + block)));
        byte[] intInc = ER302Driver.intToByteArray(i, false);
        bb.put(intInc);
        byte[] data = bb.array();
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_DECREMENT, data);
        return result;
    }

    private byte[] initBalance(char sector, char block, int i) {
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put(((byte) (sector * 4 + block)));
        byte[] intInc = ER302Driver.intToByteArray(i, false);
        bb.put(intInc);
        byte[] data = bb.array();
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_INITVAL, data);
        return result;
    }

    private byte[] writeBlock(char sector, char block, byte[] dataBlock) {
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put(((byte) (sector * 4 + block)));
        bb.put(dataBlock);
        byte[] data = bb.array();
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_WRITE_BLOCK, data);
        return result;
    }

    private byte[] readBlock(char sector, char block) {
        byte[] data = {(byte) (sector * 4 + block)};
        byte[] result = buildCommand(ER302Driver.CMD_MIFARE_READ_BLOCK, data);
        System.out.println("readBlock command: " + ER302Driver.byteArrayToHexString(result));
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
            logArea.clear();
            state = 0;
            byte[] statusMsg = buildCommand(ER302Driver.CMD_WORKING_STATUS, new byte[]{0x01, 0x23});
            lastCommand = new ER302Driver.CommandStruct(0, "Working status", statusMsg);
            commandMap.put(0, lastCommand);
            addCommand(new ER302Driver.CommandStruct(1, "Firmware version", readFirmware()));
            addCommand(new ER302Driver.CommandStruct(2, "MiFare request", mifareRequest()));

            serialPort.writeBytes(statusMsg);
        } catch (SerialPortException ex) {
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
        if (portNames.length > 0) {
            serialPortList.getSelectionModel().select(0);
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
                log("received bytes count: " + count);
                try {
                    byte[] buffer = serialPort.readBytes(count);
                    bout.write(buffer);
                    buffer = bout.toByteArray();
                    while ((buffer.length >= 2) && !Arrays.equals(Arrays.copyOf(buffer, 2), ER302Driver.HEADER)) {
                        buffer = Arrays.copyOfRange(buffer, 1, buffer.length);
                    }

                    String input = ER302Driver.byteArrayToHexString(buffer); //"distance:50 mm" 
                    log("received[" + input + "]");
                    ER302Driver.ReceivedStruct result = ER302Driver.decodeReceivedData(buffer);
                    while ((result != null) && (result.length > 0)) {
                        iterateCommands(result);
                        if (result.length < buffer.length) {
                            buffer = Arrays.copyOfRange(buffer, result.length, buffer.length);
                        } else {
                            buffer = null;
                        }
                        bout.reset();
                        if ((buffer != null) && buffer.length > 0) {
                            bout.write(buffer);
                            result = ER302Driver.decodeReceivedData(buffer);
                        } else {
                            result = null;
                        }

                    }
                    if (commands.size() > 0) {
                        lastCommand = commands.poll();
                        if (lastCommand != null) {
                            serialPort.writeBytes(lastCommand.getCmd());
                        }
                    }
                } catch (SerialPortException ex) {
                    Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
                    log(ex.getMessage());
                } catch (IOException ex) {
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
                bout.close();
                bout = null;
                updateButtons(false);
                log("Disconnected.");
            } catch (SerialPortException ex) {
                Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
                log(ex.getMessage());
            } catch (IOException ex) {
                Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
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
                    bout = new ByteArrayOutputStream();
                    log("Connected.");
                } catch (SerialPortException ex) {
                    Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
                    log(ex.getMessage());
                    updateButtons(false);
                }
            }
        }
    }

    private void iterateCommands(ER302Driver.ReceivedStruct result) {
        if (lastCommand == null) {
            return;
        }
        log(lastCommand.id + ". " + lastCommand.descrition + " (data):" + ER302Driver.byteArrayToHexString(result.data));
        if (Arrays.equals(result.cmd, ER302Driver.CMD_READ_FW_VERSION)) {
            log("Firmware versino:" + new String(result.data));
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_ANTICOLISION)) {
            cardSerialNo = result.data;
            if (Arrays.equals(typeBytes, ER302Driver.TYPE_MIFARE_1K)) {
                log("CardType: MiFARE Classic 1K");
                addCommand(new ER302Driver.CommandStruct(4, "MifareSelect", mifareSelect(cardSerialNo)));
            } else if (Arrays.equals(typeBytes, ER302Driver.TYPE_MIFARE_UL)) {
                log("CardType: MiFARE UltraLight");
                addCommand(new ER302Driver.CommandStruct(4, "MifareSelect", mifareULSelect()));
            }
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_REQUEST)) {
            typeBytes = result.data;
            addCommand(new ER302Driver.CommandStruct(3, "Mifare anticolision", mifareAnticolision()));
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_SELECT) || Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_UL_SELECT)) {
            addCommand(new ER302Driver.CommandStruct(5, "Auth2", auth2((char) 7)));
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_AUTH2)) {
            if (state == 0) {
                addCommand(new ER302Driver.CommandStruct(6, "Init balance (7/1)", initBalance((char) 7, (char) 1, 10)));
            } else if (state == 1) {
                addCommand(new ER302Driver.CommandStruct(8, "Read balance (7/1)", readBalance((char) 7, (char) 1)));
            } else if (state == 2) {
                addCommand(new ER302Driver.CommandStruct(10, "Inc balance (7/1)", incBalance((char) 7, (char) 1, 2)));
            } else if (state == 3) {
                addCommand(new ER302Driver.CommandStruct(12, "Read balance (7/1)", readBalance((char) 7, (char) 1)));
            } else if (state == 4) {
                addCommand(new ER302Driver.CommandStruct(14, "Dec balance (7/1)", decBalance((char) 7, (char) 1, 2)));
            } else if (state == 5) {
                addCommand(new ER302Driver.CommandStruct(16, "Read balance (7/1)", readBalance((char) 7, (char) 1)));
            } else if (state == 6) {
                addCommand(new ER302Driver.CommandStruct(18, "Read block (7/1)", readBlock((char) 7, (char) 0)));
            } else if (state == 7) {
                byte[] byteBlock = {0x00, 0x01, 0x02, 0x03};
                addCommand(new ER302Driver.CommandStruct(20, "Write block (7/1)", writeBlock((char) 7, (char) 0, byteBlock)));
            } else if (state == 8) {
                addCommand(new ER302Driver.CommandStruct(22, "Read block (7/1)", readBlock((char) 7, (char) 0)));
            }
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_INITVAL)) {
            addCommand(new ER302Driver.CommandStruct(7, "Auth2", auth2((char) 7)));
            state++;
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_INCREMENT)) {
            addCommand(new ER302Driver.CommandStruct(7 + (2 * state), "Auth2", auth2((char) 7)));
            state++;
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_DECREMENT)) {
            addCommand(new ER302Driver.CommandStruct(7 + (2 * state), "Auth2", auth2((char) 7)));
            state++;
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_READ_BALANCE)) {
            int value = ER302Driver.byteArrayToInteger(result.data, false);
            log("Read balance decimal(" + value + ")");
            addCommand(new ER302Driver.CommandStruct(7 + (2 * state), "Auth", auth2((char) 7)));
            state++;
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_READ_BLOCK)) {
            addCommand(new ER302Driver.CommandStruct(7 + (2 * state), "Auth", auth2((char) 7)));
            state++;
        } else if (Arrays.equals(result.cmd, ER302Driver.CMD_MIFARE_WRITE_BLOCK)) {
            addCommand(new ER302Driver.CommandStruct(7 + (2 * state), "Auth", auth2((char) 7)));
            state++;
        }
    }

    class MessageLogTask extends Task<Void> {

        String msg;

        public MessageLogTask(String msg) {
            this.msg = msg;
        }

        @Override
        protected Void call() throws Exception {
            logArea.appendText(msg + newLine);
            return null;
        }

    }

    private void addCommand(ER302Driver.CommandStruct cmd) {
        commandMap.put(cmd.id, cmd);
        commands.add(cmd);
    }

    private void log(String msg) {
        System.out.println(msg);
        if (msg != null) {
            MessageLogTask task = new MessageLogTask(msg);
            Platform.runLater(task);
        }
    }

    private void updateButtons(boolean connected) {
        connectButton.setDisable(connected);
        disconnectButton.setDisable(!connected);
        readCardIdButton.setDisable(!connected);
        beepButton.setDisable(!connected);
        serialPortList.setDisable(connected);
    }

}
