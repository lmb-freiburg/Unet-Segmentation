import ij.IJ;
import ij.Prefs;
import ij.WindowManager;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.measure.Calibration;

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JDialog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

public class UnetSegmentationJob extends UnetJob implements PlugIn {

  private File _localTmpFile = null;

  private ImagePlus _imp = null;
  private String _impName = null;
  private int[] _impShape = {0, 0, 0, 0, 0};
  private Calibration _impCalibration = null;

  private JCheckBox _keepOriginalCheckBox = new JCheckBox(
      "Keep original", Prefs.get("unet_segmentation.keepOriginal", false));
  private JCheckBox _outputScoresCheckBox = new JCheckBox(
      "Output scores", Prefs.get("unet_segmentation.outputScores", false));

  public void setImagePlus(ImagePlus imp) {
    _imp = imp;
    if (_imp != null) {
      _impName = _imp.getTitle();
      _impShape = _imp.getDimensions();
      _impCalibration = _imp.getCalibration().copy();
    }
  }

  @Override
  public String imageName() {
    return _impName;
  }

  public int[] imageShape() {
    return _impShape;
  }

  public Calibration imageCalibration() {
    return _impCalibration;
  }

  @Override
  public boolean ready() {
    return _readyCancelButton.getText().equals("Show");
  }

  @Override
  protected void setReady(boolean ready) {
    _readyCancelButton.setText("Show");
    if (_jobTableModel == null) return;
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            _jobTableModel.updateJobDownloadEnabled(id());
          }});
  }

  @Override
  public void finish() {
    if (_finished) return;
    try {
      UnetTools.loadSegmentationToImagePlus(
          _localTmpFile, this, _outputScoresCheckBox.isSelected());
      cleanUp();
      if (Recorder.record) {
        Recorder.setCommand(null);
        String command = "call('UnetSegmentationJob.segmentHyperStack', " +
            "'modelFilename=" + model().file.getAbsolutePath() +
            ",weightsFilename=" + weightsFileName() +
            "," + model().getTilingParameterString() +
            ",gpuId=" + (String)_useGPUComboBox.getSelectedItem() +
            ",useRemoteHost=" + String.valueOf(_sshSession != null);
        if (_sshSession != null) {
          command +=
              ",hostname=" + _sshSession.getHost() +
              ",port=" + String.valueOf(_sshSession.getPort()) +
              ",username=" + _sshSession.getUserName();
          if (_hostConfiguration.authRSAKey())
              command += ",RSAKeyfile=" + _hostConfiguration.rsaKeyFile();
        }
        command +=
            ",processFolder=" + _processFolderTextField.getText() +
            ",keepOriginal=" + String.valueOf(
                _keepOriginalCheckBox.isSelected()) +
            ",outputScores=" + String.valueOf(
                _outputScoresCheckBox.isSelected()) + "');";
        Recorder.recordString(command);
      }
    }
    catch (IOException e) {
      IJ.error(e.toString());
    }
    catch (Exception e) {
      IJ.error(e.toString());
    }
  }

  @Override
  public void prepareParametersDialog() {

    super.prepareParametersDialog();

    // Create config panel
    _configPanel.add(_keepOriginalCheckBox);
    _configPanel.add(_outputScoresCheckBox);

    // Finalize the dialog
    _parametersDialog.pack();
    _parametersDialog.setMinimumSize(
        _parametersDialog.getPreferredSize());
    _parametersDialog.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE,
            _parametersDialog.getPreferredSize().height));
    _parametersDialog.setLocationRelativeTo(_imp.getWindow());
  }

  public boolean getParameters() {
    if (_imp == null) setImagePlus(WindowManager.getCurrentImage());
    if (_imp == null) {
      IJ.showMessage(
          "U-Net segmentation requires an open hyperstack to segment.");
      return false;
    }

    boolean dialogOK = false;
    while (!dialogOK) {
      dialogOK = true;
      _parametersDialog.setVisible(true);

      // Dialog was cancelled
      if (!_parametersDialog.isDisplayable()) {
        cleanUp();
        return false;
      }

      dialogOK = checkParameters();
      if (!dialogOK) continue;

      if (!(model().nChannels == _imp.getNChannels() ||
            (model().nChannels == 3 &&
             (_imp.getType() == ImagePlus.COLOR_256 ||
              _imp.getType() == ImagePlus.COLOR_RGB)))) {
        IJ.showMessage("The selected model cannot segment " +
                       _imp.getNChannels() + "-channel images.");
        dialogOK = false;
        continue;
      }

      // Check whether caffe unet binary exists and is executable
      ProcessResult res = null;
      if (_sshSession == null) {
        try {
          Vector<String> cmd = new Vector<String>();
          cmd.add(Prefs.get("unet_segmentation.caffeUnetBinary", "caffe_unet"));
          res = UnetTools.execute(cmd, this);
        }
        catch (InterruptedException e) {
          cleanUp();
          return false;
        }
        catch (IOException e) {
          res.exitStatus = 1;
        }
      }
      else {
        try {
          String cmd = Prefs.get(
              "unet_segmentation.caffeUnetBinary", "caffe_unet");
          res = UnetTools.execute(cmd, _sshSession, this);
        }
        catch (InterruptedException e) {
          cleanUp();
          return false;
        }
        catch (JSchException e) {
          res.exitStatus = 1;
        }
        catch (IOException e) {
          res.exitStatus = 1;
        }
      }
      if (res.exitStatus != 0) {
        String caffePath = JOptionPane.showInputDialog(
            _imp.getWindow(), "caffe_unet was not found.\n" +
            "Please specify your caffe_unet binary\n",
            Prefs.get("unet_segmentation.caffeUnetBinary", "caffe_unet"));
        if (caffePath == null) {
          cleanUp();
          return false;
        }
        if (caffePath.equals(""))
            Prefs.set("unet_segmentation.caffeUnetBinary", "caffe_unet");
        else Prefs.set("unet_segmentation.caffeUnetBinary", caffePath);
        dialogOK = false;
        continue;
      }

      // Check whether combination of model and weights can be used for
      // segmentation
      if (_sshSession != null) {
        model().remoteAbsolutePath = processFolder() + "/" + id() + "_model.h5";
        try {
          _createdRemoteFolders.addAll(
              UnetTools.put(
                  model().file, model().remoteAbsolutePath, _sshSession, this));
          _createdRemoteFiles.add(model().remoteAbsolutePath);
          Prefs.set("unet_segmentation.processfolder", processFolder());
        }
        catch (InterruptedException e) {
          cleanUp();
          return false;
        }
        catch (SftpException e) {
          dialogOK = false;
          IJ.showMessage(
              "Model upload failed.\nDo you have sufficient " +
              "permissions to create the processing folder on " +
              "the remote host?");
          continue;
        }
        catch (JSchException e) {
          dialogOK = false;
          IJ.showMessage(
              "Model upload failed.\nDo you have sufficient " +
              "permissions to create the processing folder on " +
              "the remote host?");
          continue;
        }
        catch (IOException e) {
          dialogOK = false;
          IJ.showMessage("Model upload failed. Could not read model file.");
          continue;
        }

        try {
          do {
            String cmd =
                Prefs.get("unet_segmentation.caffeBinary", "caffe_unet") +
                " check_model_and_weights_h5 -model \"" +
                model().remoteAbsolutePath + "\" -weights \"" +
                weightsFileName() + "\" " + caffeGPUParameter();
            res = UnetTools.execute(cmd, _sshSession, this);
            if (res.exitStatus != 0) {
              int selectedOption = JOptionPane.showConfirmDialog(
                  _imp.getWindow(), "No trained weights found at the " +
                  "given location on the backend server.\nDo you want " +
                  "to upload the weights now?", "Upload weights?",
                  JOptionPane.YES_NO_CANCEL_OPTION,
                  JOptionPane.QUESTION_MESSAGE);
              switch (selectedOption) {
              case JOptionPane.YES_OPTION: {
                File startFile =
                    (model() == null || model().file == null ||
                     model().file.getParentFile() == null) ?
                    new File(".") : model().file.getParentFile();
                JFileChooser f = new JFileChooser(startFile);
                f.setDialogTitle("Select trained U-net weights");
                f.setFileFilter(
                    new FileNameExtensionFilter(
                        "HDF5 and prototxt files", "h5", "H5",
                        "prototxt", "PROTOTXT", "caffemodel", "CAFFEMODEL"));
                f.setMultiSelectionEnabled(false);
                f.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int res2 = f.showDialog(_imp.getWindow(), "Select");
                if (res2 != JFileChooser.APPROVE_OPTION)
                    throw new InterruptedException("Aborted by user");
                try {
                  UnetTools.put(
                      f.getSelectedFile(), weightsFileName(),
                      _sshSession, this);
                }
                catch (SftpException e) {
                  res.exitStatus = 3;
                  res.shortErrorString =
                      "Upload failed.\nDo you have sufficient " +
                      "permissions to create a file at the given " +
                      "backend server path?";
                  res.cerr = e.getMessage();
                  break;
                }
                break;
              }
              case JOptionPane.NO_OPTION: {
                res.exitStatus = 2;
                res.shortErrorString = "Weight file selection required";
                res.cerr = "Weight file " +
                    weightsFileName() + " not found";
                break;
              }
              case JOptionPane.CANCEL_OPTION:
              case JOptionPane.CLOSED_OPTION:
                throw new InterruptedException("Aborted by user");
              }
              if (res.exitStatus > 1) break;
            }
          }
          while (res.exitStatus != 0);
        }
        catch (InterruptedException e) {
          cleanUp();
          return false;
        }
        catch (JSchException e) {
          res.exitStatus = 1;
          res.shortErrorString = "SSH connection error";
          res.cerr = e.getMessage();
        }
        catch (IOException e) {
          res.exitStatus = 1;
          res.shortErrorString = "Input/Output error";
          res.cerr = e.getMessage();
        }
      }
      else {
        try {
          Vector<String> cmd = new Vector<String>();
          cmd.add(Prefs.get("unet_segmentation.caffeBinary", "caffe_unet"));
          cmd.add("check_model_and_weights_h5");
          cmd.add("-model");
          cmd.add(model().file.getAbsolutePath());
          cmd.add("-weights");
          cmd.add(weightsFileName());
          if (!caffeGPUParameter().equals("")) {
            cmd.add(caffeGPUParameter().split(" ")[0]);
            cmd.add(caffeGPUParameter().split(" ")[1]);
          }
          res = UnetTools.execute(cmd, this);
        }
        catch (InterruptedException e) {
          cleanUp();
          return false;
        }
        catch (IOException e) {
          res.exitStatus = 1;
          res.shortErrorString = "Input/Output error";
          res.cerr = e.getMessage();
        }
      }
      if (res.exitStatus != 0) {
        dialogOK = false;

        // User decided to change weight file, so don't bother him with
        // additional message boxes
        if (res.exitStatus == 2) continue;

        IJ.log(res.cerr);
        IJ.showMessage("Model/Weight check failed:\n" + res.shortErrorString);
        continue;
      }
    }

    Prefs.set("unet_segmentation.keepOriginal",
              _keepOriginalCheckBox.isSelected());
    Prefs.set("unet_segmentation.outputScores",
              _outputScoresCheckBox.isSelected());

    _parametersDialog.dispose();

    if (_jobTableModel != null) _jobTableModel.fireTableDataChanged();

    return true;
  }

  public void runUnetSegmentation(
      String fileName, Session session)
      throws JSchException, IOException, InterruptedException {
    _taskStatus.isIndeterminate = true;
    setTaskProgress("Initializing U-Net", 0, 0);
    String gpuParm = new String();
    String selectedGPU = (String)_useGPUComboBox.getSelectedItem();
    if (selectedGPU.contains("GPU "))
        gpuParm = "-gpu " + selectedGPU.substring(selectedGPU.length() - 1);
    else if (selectedGPU.contains("all")) gpuParm = "-gpu all";

    String nTilesParm = model().getCaffeTilingParameter();

    String commandString =
        Prefs.get("unet_segmentation.caffeBinary", "caffe_unet") +
        " tiled_predict -infileH5 \"" + fileName +
        "\" -outfileH5 \"" + fileName + "\" -model \"" +
        model().remoteAbsolutePath + "\" -weights \"" +
        weightsFileName() + "\" -iterations 0 " +
        nTilesParm + " " + gpuParm;

    IJ.log(commandString);

    Channel channel = session.openChannel("exec");
    ((ChannelExec)channel).setCommand(commandString);

    InputStream stdError = ((ChannelExec)channel).getErrStream();
    InputStream stdOutput = channel.getInputStream();

    channel.connect();

    byte[] buf = new byte[1024];
    String errorMsg = new String();
    String outMsg = new String();
    int exitStatus = -1;
    try {
      while (true) {
        while(stdOutput.available() > 0) {
          int i = stdOutput.read(buf, 0, 1024);
          if (i < 0) break;
          outMsg += "\n" + new String(buf, 0, i);
        }
        while(stdError.available() > 0) {
          int i = stdError.read(buf, 0, 1024);
          if (i < 0) break;
          errorMsg += "\n" + new String(buf, 0, i);
        }
        int idx = -1;
        while ((idx = outMsg.indexOf('\n')) != -1) {
          String line = outMsg.substring(0, idx);
          outMsg = outMsg.substring(idx + 1);
          if (line.regionMatches(0, "Processing batch ", 0, 17)) {
            line = line.substring(17);
            int sepPos = line.indexOf('/');
            int batchIdx = Integer.parseInt(line.substring(0, sepPos));
            line = line.substring(sepPos + 1);
            sepPos = line.indexOf(',');
            int nBatches = Integer.parseInt(line.substring(0, sepPos));
            line = line.substring(sepPos + 7);
            sepPos = line.indexOf('/');
            int tileIdx = Integer.parseInt(line.substring(0, sepPos));
            line = line.substring(sepPos + 1);
            int nTiles = Integer.parseInt(line);
            setTaskProgress(
                "Segmenting batch " + String.valueOf(batchIdx) + "/" +
                String.valueOf(nBatches) + ", tile " +
                String.valueOf(tileIdx) + "/" + String.valueOf(nTiles),
                (batchIdx - 1) * nTiles + tileIdx - 1, nBatches * nTiles);
            setProgress(
                (int) (_taskProgressMin +
                       (float) ((batchIdx - 1) * nTiles + tileIdx - 1) /
                       (float) (nBatches * nTiles) *
                       (_taskProgressMax - _taskProgressMin)));
          }
        }
        if (channel.isClosed()) {
          if(stdOutput.available() > 0 || stdError.available() > 0) continue;
          exitStatus = channel.getExitStatus();
          break;
        }
        if (interrupted()) throw new InterruptedException();
        Thread.sleep(100);
      }
    }
    catch (InterruptedException e) {
      _readyCancelButton.setText("Terminating...");
      _readyCancelButton.setEnabled(false);
      try {
        channel.sendSignal("TERM");
        int graceMilliSeconds = 10000;
        int timeElapsedMilliSeconds = 0;
        while (!channel.isClosed() &&
               timeElapsedMilliSeconds <= graceMilliSeconds) {
          timeElapsedMilliSeconds += 100;
          try {
            Thread.sleep(100);
          }
          catch (InterruptedException eInner) {}
        }
        if (!channel.isClosed()) channel.sendSignal("KILL");
      }
      catch (Exception eInner) {
        IJ.log(
            "Process could not be terminated using SIGTERM: " + eInner);
      }
      channel.disconnect();
      throw e;
    }
    channel.disconnect();

    if (exitStatus != 0) {
      IJ.log(errorMsg);
      throw new IOException(
          "Error during segmentation: exit status " + exitStatus +
          "\nSee log for further details");
    }
  }

  public void runUnetSegmentation(File file)
      throws IOException, InterruptedException {
    setTaskProgress("Initializing U-Net", 0, 0);
    String gpuAttribute = new String();
    String gpuValue = new String();
    String selectedGPU = (String)_useGPUComboBox.getSelectedItem();
    if (selectedGPU.contains("GPU ")) {
      gpuAttribute = "-gpu";
      gpuValue = selectedGPU.substring(selectedGPU.length() - 1);
    }
    else if (selectedGPU.contains("all")) {
      gpuAttribute = "-gpu";
      gpuValue = "all";
    }

    String[] parameters = model().getCaffeTilingParameter().split("\\s");
    String nTilesAttribute = parameters[0];
    String nTilesValue = parameters[1];

    String commandString =
        Prefs.get("unet_segmentation.caffeBinary", "caffe_unet");

    IJ.log(
        commandString + " tiled_predict -infileH5 \"" +
        file.getAbsolutePath() + "\" -outfileH5 \"" +
        file.getAbsolutePath() + "\" -model \"" +
        model().file.getAbsolutePath() + "\" -weights \"" +
        weightsFileName() + "\" -iterations 0 " +
        nTilesAttribute + " " + nTilesValue + " " + gpuAttribute + " " +
        gpuValue);
    ProcessBuilder pb;
    if (!gpuAttribute.equals(""))
        pb = new ProcessBuilder(
            commandString, "tiled_predict", "-infileH5",
            file.getAbsolutePath(), "-outfileH5", file.getAbsolutePath(),
            "-model", model().file.getAbsolutePath(), "-weights",
            weightsFileName(), "-iterations", "0",
            nTilesAttribute, nTilesValue, gpuAttribute, gpuValue);
    else
        pb = new ProcessBuilder(
            commandString, "tiled_predict", "-infileH5",
            file.getAbsolutePath(), "-outfileH5", file.getAbsolutePath(),
            "-model", model().file.getAbsolutePath(), "-weights",
            weightsFileName(), "-iterations", "0",
            nTilesAttribute, nTilesValue);

    Process p = pb.start();

    BufferedReader stdOutput =
        new BufferedReader(new InputStreamReader(p.getInputStream()));
    BufferedReader stdError =
        new BufferedReader(new InputStreamReader(p.getErrorStream()));

    int exitStatus = -1;
    String line;
    String errorMsg = "";
    try {
      while (true) {
        // Check for ready() to avoid thread blocking, then read
        // all available lines from the buffer and update progress
        while (stdOutput.ready()) {
          line = stdOutput.readLine();
          if (line.regionMatches(0, "Processing batch ", 0, 17)) {
            line = line.substring(17);
            int sepPos = line.indexOf('/');
            int batchIdx = Integer.parseInt(line.substring(0, sepPos));
            line = line.substring(sepPos + 1);
            sepPos = line.indexOf(',');
            int nBatches = Integer.parseInt(line.substring(0, sepPos));
            line = line.substring(sepPos + 7);
            sepPos = line.indexOf('/');
            int tileIdx = Integer.parseInt(line.substring(0, sepPos));
            line = line.substring(sepPos + 1);
            int nTiles = Integer.parseInt(line);
            setTaskProgress(
                "Segmenting batch " + String.valueOf(batchIdx) + "/" +
                String.valueOf(nBatches) + ", tile " +
                String.valueOf(tileIdx) + "/" + String.valueOf(nTiles),
                (batchIdx - 1) * nTiles + tileIdx - 1, nBatches * nTiles);
            setProgress(
                (int) (_taskProgressMin +
                       (float) ((batchIdx - 1) * nTiles + tileIdx - 1) /
                       (float) (nBatches * nTiles) *
                       (_taskProgressMax - _taskProgressMin)));
          }
        }
        // Also read error stream to avoid stream overflow that leads
        // to process stalling
        while (stdError.ready()) {
          line = stdError.readLine();
          errorMsg += line + "\n";
        }

        try {
          exitStatus = p.exitValue();
          break;
        }
        catch (IllegalThreadStateException e) {}
        if (interrupted()) throw new InterruptedException();
        Thread.sleep(100);
      }
    }
    catch (InterruptedException e) {
      _readyCancelButton.setText("Terminating...");
      _readyCancelButton.setEnabled(false);
      p.destroy();
      throw e;
    }

    if (exitStatus != 0) {
      IJ.log(errorMsg);
      throw new IOException(
          "Error during segmentation: exit status " + exitStatus +
          "\nSee log for further details");
    }

    setTaskProgress(1, 1);
  }

  public static void segmentHyperStack(String params)
      throws InterruptedException {
    final UnetSegmentationJob job = new UnetSegmentationJob();
    if (job._imp == null)
        job.setImagePlus(WindowManager.getCurrentImage());
    if (job._imp == null) {
      IJ.error(
          "U-Net Segmentation", "No image selected for segmentation.");
      return;
    }

    String[] parameterStrings = params.split(",");
    Map<String,String> parameters = new HashMap<String,String>();
    for (int i = 0; i < parameterStrings.length; i++)
        parameters.put(parameterStrings[i].split("=")[0],
                       parameterStrings[i].split("=")[1]);
    job._modelComboBox.removeAllItems();
    ModelDefinition model = new ModelDefinition();
    job._modelComboBox.addItem(model);
    job._modelComboBox.setSelectedItem(model);
    job.model().load(new File(parameters.get("modelFilename")));
    job._weightsFileTextField.setText(parameters.get("weightsFilename"));
    job.model().setFromTilingParameterString(parameterStrings[2]);
    job._useGPUComboBox.setSelectedItem(parameters.get("gpuId"));
    if (Boolean.valueOf(parameters.get("useRemoteHost"))) {
      try {
        String hostname = parameters.get("hostname");
        int port = Integer.valueOf(parameters.get("port"));
        String username = parameters.get("username");
        JSch jsch = new JSch();
        jsch.setKnownHosts(
            new File(System.getProperty("user.home") +
                     "/.ssh/known_hosts").getAbsolutePath());
        if (parameters.containsKey("RSAKeyfile"))
            jsch.addIdentity(parameters.get("RSAKeyfile"));
        job._sshSession = jsch.getSession(username, hostname, port);
        job._sshSession.setUserInfo(new MyUserInfo());

        if (!parameters.containsKey("RSAKeyfile")) {
          final JDialog passwordDialog = new JDialog(
              job._imp.getWindow(), "U-Net Segmentation", true);
          JPanel mainPanel = new JPanel();
          mainPanel.add(new JLabel("Password:"));
          final JPasswordField passwordField = new JPasswordField(15);
          mainPanel.add(passwordField);
          passwordDialog.add(mainPanel, BorderLayout.CENTER);
          JButton okButton = new JButton("OK");
          okButton.addActionListener(
              new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  char[] password = passwordField.getPassword();
                  byte[] passwordAsBytes =
                      HostConfigurationPanel.toBytes(password);
                  job._sshSession.setPassword(passwordAsBytes);
                  Arrays.fill(passwordAsBytes, (byte) 0);
                  Arrays.fill(password, '\u0000');
                  passwordField.setText("");
                  passwordDialog.dispose();
                }});
          passwordDialog.add(okButton, BorderLayout.SOUTH);
          passwordDialog.getRootPane().setDefaultButton(okButton);
          passwordDialog.pack();
          passwordDialog.setMinimumSize(passwordDialog.getPreferredSize());
          passwordDialog.setMaximumSize(passwordDialog.getPreferredSize());
          passwordDialog.setLocationRelativeTo(job._imp.getWindow());
          passwordDialog.setVisible(true);
        }

        job._sshSession.connect();
      }
      catch (JSchException e) {
        IJ.log("Macro call to UnetJob.segmentHyperStack aborted. " +
               "Could not establish SSH connection.");
        IJ.error("UnetJob.segmentHyperStack",
                 "Could not establish SSH connection.");
        return;
      }
    }
    job._processFolderTextField.setText(parameters.get("processFolder"));
    job._keepOriginalCheckBox.setSelected(
        Boolean.valueOf(parameters.get("keepOriginal")));
    job._outputScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputScores")));
    job._isInteractive = false;

    job.start();
    job.join();
    job.finish();
  }

  @Override
  public void run(String arg) {
    setImagePlus(WindowManager.getCurrentImage());
    if (_imp == null) {
      IJ.error(
          "U-Net Segmentation", "No image selected for segmentation.");
      return;
    }
    prepareParametersDialog();
    try {
      start();
      join();
      finish();
    }
    catch (InterruptedException e) {}
    IJ.showProgress(1.0);
  }

  @Override
  public void run() {
    setProgress(0);
    if (_isInteractive && !getParameters()) return;
    try {
      if (_sshSession != null) {
        if (!_isInteractive) {
          model().remoteAbsolutePath =
              _processFolderTextField.getText() + "/" + id() + "_model.h5";
          _createdRemoteFolders.addAll(
              UnetTools.put(
                  model().file, model().remoteAbsolutePath, _sshSession, this));
          _createdRemoteFiles.add(model().remoteAbsolutePath);
        }
        setProgress(1);

        _localTmpFile = File.createTempFile(id(), ".h5");
        _localTmpFile.delete();

        String remoteFileName = _processFolderTextField.getText() + "/" +
            id() + ".h5";

        setImagePlus(
            UnetTools.saveHDF5Blob(
                _imp, _localTmpFile, this, _keepOriginalCheckBox.isSelected()));
        _impShape = _imp.getDimensions();
        if (interrupted()) throw new InterruptedException();
        setProgress(2);

        setTaskProgressRange(3, 10);
        _createdRemoteFolders.addAll(
            UnetTools.put(_localTmpFile, remoteFileName, _sshSession, this));
        _createdRemoteFiles.add(remoteFileName);
        if (interrupted()) throw new InterruptedException();
        setProgress(10);

        setTaskProgressRange(11, 90);
        runUnetSegmentation(remoteFileName, _sshSession);
        if (interrupted()) throw new InterruptedException();
        setProgress(90);

        setTaskProgressRange(91, 100);
        UnetTools.get(remoteFileName, _localTmpFile, _sshSession, this);
        if (interrupted()) throw new InterruptedException();
        setProgress(100);
      }
      else {
        _localTmpFile = new File(
            _processFolderTextField.getText() + "/" + id() + ".h5");

        setImagePlus(
            UnetTools.saveHDF5Blob(
                _imp, _localTmpFile, this, _keepOriginalCheckBox.isSelected()));
        _impShape = _imp.getDimensions();
        if (interrupted()) throw new InterruptedException();
        setProgress(2);

        setTaskProgressRange(3, 100);
        runUnetSegmentation(_localTmpFile);
        if (interrupted()) throw new InterruptedException();
        setProgress(100);
      }
      setReady(true);
    }
    catch (InterruptedException e) {
      IJ.showMessage("Job " + id() + " canceled. Cleaning up.");
      cleanUp();
      if (_jobTableModel != null) _jobTableModel.deleteJob(this);
    }
    catch (Exception e) {
      IJ.error(e.toString());
      cleanUp();
      if (_jobTableModel != null) _jobTableModel.deleteJob(this);
    }
  }

};
