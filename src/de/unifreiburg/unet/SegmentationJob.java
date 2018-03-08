/**************************************************************************
 *
 * Copyright (C) 2018 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 **************************************************************************/

package de.unifreiburg.unet;

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
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JDialog;
import javax.swing.GroupLayout;

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

public class SegmentationJob extends Job implements PlugIn {

  protected File _localTmpFile = null;

  protected ImagePlus _imp = null;
  protected String _impName = null;
  protected int[] _impShape = {0, 0, 0, 0, 0};
  protected Calibration _impCalibration = null;

  protected final String[] _averagingModes = { "none", "mirror", "rotate" };
  protected JComboBox<String> _averagingComboBox =
      new JComboBox<String>(_averagingModes);
  protected JCheckBox _keepOriginalCheckBox = new JCheckBox(
      "Keep original", Prefs.get("unet_segmentation.keepOriginal", false));
  protected JCheckBox _outputScoresCheckBox = new JCheckBox(
      "Show scores", Prefs.get("unet_segmentation.outputScores", false));
  protected JCheckBox _outputSoftmaxScoresCheckBox = new JCheckBox(
      "Show softmax scores",
      Prefs.get("unet_segmentation.outputSoftmaxScores", false));

  public SegmentationJob() {
    super();
  }

  public SegmentationJob(JobTableModel model) {
    super(model);
  }

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
    return readyCancelButton().getText().equals("Show");
  }

  @Override
  protected void setReady(boolean ready) {
    if (ready() == ready) return;
    if (ready) {
      readyCancelButton().setText("Show");
      if (jobTable() == null) finish();
      else
          SwingUtilities.invokeLater(
              new Runnable() {
                @Override
                public void run() {
                  jobTable().updateJobDownloadEnabled(id());
                }
              });
    }
    else readyCancelButton().setText("Cancel");
  }

  @Override
  public void finish() {
    if (progressMonitor().finished()) return;
    readyCancelButton().setEnabled(false);
    try {
      new Thread()
      {
        @Override
        public void run() {
          try {
            Tools.loadSegmentationToImagePlus(
                _localTmpFile, SegmentationJob.this,
                _outputScoresCheckBox.isSelected(),
                _outputSoftmaxScoresCheckBox.isSelected());
            if (Recorder.record) {
              Recorder.setCommand(null);
              String command =
                  "call('de.unifreiburg.unet.SegmentationJob." +
                  "processHyperStack', " +
                  "'modelFilename=" + model().file.getAbsolutePath() +
                  ",weightsFilename=" + weightsFileName() +
                  "," + model().getTilingParameterString() +
                  ",gpuId=" + selectedGPUString() +
                  ",useRemoteHost=" + String.valueOf(sshSession() != null);
              if (sshSession() != null) {
                command +=
                    ",hostname=" + sshSession().getHost() +
                    ",port=" + String.valueOf(sshSession().getPort()) +
                    ",username=" + sshSession().getUserName();
                if (hostConfiguration().authRSAKey())
                    command += ",RSAKeyfile=" +
                        hostConfiguration().rsaKeyFile();
              }
              command +=
                  ",processFolder=" + processFolder() +
                  ",average=" + (String)_averagingComboBox.getSelectedItem() +
                  ",keepOriginal=" + String.valueOf(
                      _keepOriginalCheckBox.isSelected()) +
                  ",outputScores=" + String.valueOf(
                      _outputScoresCheckBox.isSelected()) +
                  ",outputSoftmaxScores=" + String.valueOf(
                      _outputSoftmaxScoresCheckBox.isSelected()) + "');\n";
              Recorder.recordString(command);
            }
          }
          catch (IOException e) {
            IJ.error("U-Net Segmentation", e.toString());
          }
          finishJob();
        }
      }.start();
    }
    catch (IllegalThreadStateException e) {}
  }

  @Override
  public void prepareParametersDialog() {

    super.prepareParametersDialog();
    _parametersDialog.setTitle("U-Net Segmentation");

    JLabel averagingModeLabel = new JLabel("Averaging:");
    _averagingComboBox.setToolTipText(
        "Use average prediction over flipped or rotated patches per pixel");

    JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);

    _horizontalDialogLayoutGroup
        .addComponent(sep)
        .addGroup(
        _dialogLayout.createSequentialGroup()
        .addComponent(averagingModeLabel)
        .addComponent(_averagingComboBox));
    _verticalDialogLayoutGroup
        .addComponent(sep)
        .addGroup(
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
        .addComponent(averagingModeLabel)
        .addComponent(_averagingComboBox));

    // Create config panel
    _configPanel.add(_keepOriginalCheckBox);
    _configPanel.add(_outputScoresCheckBox);
    _configPanel.add(_outputSoftmaxScoresCheckBox);

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

  public boolean getParameters() throws InterruptedException {

    progressMonitor().initNewTask("Checking parameters", 0.0f, 0);

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
      if (!_parametersDialog.isDisplayable())
          throw new InterruptedException("Dialog canceled");

      dialogOK = checkParameters();
      if (!dialogOK) continue;

      // Check whether caffe binary exists and is executable
      ProcessResult res = null;
      while (res == null)
      {
        if (sshSession() == null) {
          try {
            Vector<String> cmd = new Vector<String>();
            cmd.add(Prefs.get("unet_finetuning.caffeBinary", "caffe"));
            res = Tools.execute(cmd, this);
          }
          catch (IOException e) {
            res.exitStatus = 1;
          }
        }
        else {
          try {
            String cmd = Prefs.get("unet_finetuning.caffeBinary", "caffe");
            res = Tools.execute(cmd, sshSession(), this);
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
              WindowManager.getActiveWindow(), "caffe was not found.\n" +
              "Please specify your caffe binary\n",
              Prefs.get("unet_finetuning.caffeBinary", "caffe"));
          if (caffePath == null)
              throw new InterruptedException("Dialog canceled");
          if (caffePath.equals(""))
              Prefs.set("unet_finetuning.caffeBinary", "caffe");
          else Prefs.set("unet_finetuning.caffeBinary", caffePath);
          res = null;
        }
      }

      // Check whether combination of model and weights can be used for
      // segmentation
      int nChannels =
          (_imp.getType() == ImagePlus.COLOR_256 ||
           _imp.getType() == ImagePlus.COLOR_RGB) ? 3 : _imp.getNChannels();

      if (sshSession() != null) {
        model().remoteAbsolutePath = processFolder() + "/" + id() + "_model.h5";
        try {
          _createdRemoteFolders.addAll(
              Tools.put(
                  model().file, model().remoteAbsolutePath, sshSession(),
                  this));
          _createdRemoteFiles.add(model().remoteAbsolutePath);
          Prefs.set("unet_segmentation.processfolder", processFolder());
        }
        catch (SftpException|JSchException e) {
          IJ.showMessage(
              "Model upload failed.\nDo you have sufficient " +
              "permissions to create the processing folder on " +
              "the remote host?");
          dialogOK = false;
          continue;
        }
        catch (IOException e) {
          IJ.showMessage("Model upload failed. Could not read model file.");
          dialogOK = false;
          continue;
        }

        try {
          do {
            String cmd =
                Prefs.get("unet_segmentation.caffeBinary", "caffe_unet") +
                " check_model_and_weights_h5 -model \"" +
                model().remoteAbsolutePath + "\" -weights \"" +
                weightsFileName() + "\" -n_channels " + nChannels + " " +
                caffeGPUParameter();
            res = Tools.execute(cmd, sshSession(), this);
            if (res.exitStatus != 0) {
              int selectedOption = JOptionPane.showConfirmDialog(
                  _imp.getWindow(), "No compatible weights found at the " +
                  "given location on the backend server.\nDo you want " +
                  "to upload weights from the local machine now?",
                  "Upload weights?", JOptionPane.YES_NO_CANCEL_OPTION,
                  JOptionPane.QUESTION_MESSAGE);
              switch (selectedOption) {
              case JOptionPane.YES_OPTION: {
                File startFile =
                    (model() == null || model().file == null ||
                     model().file.getParentFile() == null) ?
                    new File(".") : model().file.getParentFile();
                JFileChooser f = new JFileChooser(startFile);
                f.setDialogTitle("Select trained U-Net weights");
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
                  Tools.put(
                      f.getSelectedFile(), weightsFileName(),
                      sshSession(), this);
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
          cmd.add("-n_channels");
          cmd.add(new Integer(nChannels).toString());
          if (!caffeGPUParameter().equals("")) {
            cmd.add(caffeGPUParameter().split(" ")[0]);
            cmd.add(caffeGPUParameter().split(" ")[1]);
          }
          res = Tools.execute(cmd, this);
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
    Prefs.set("unet_segmentation.outputSoftmaxScores",
              _outputSoftmaxScoresCheckBox.isSelected());

    _parametersDialog.dispose();

    if (jobTable() != null) jobTable().fireTableDataChanged();

    return true;
  }

  public void runSegmentation(
      String fileName, Session session)
      throws JSchException, IOException, InterruptedException {

    String gpuParm = caffeGPUParameter();
    String nTilesParm = model().getCaffeTilingParameter();
    String averagingParm = new String();
    if (((String)_averagingComboBox.getSelectedItem()).equals("mirror"))
        averagingParm = "-average_mirror";
    else if (((String)_averagingComboBox.getSelectedItem()).equals("rotate"))
        averagingParm = "-average_rotate";

    String commandString =
        Prefs.get("unet_segmentation.caffeBinary", "caffe_unet") +
        " tiled_predict -infileH5 \"" + fileName +
        "\" -outfileH5 \"" + fileName + "\" -model \"" +
        model().remoteAbsolutePath + "\" -weights \"" +
        weightsFileName() + "\" -iterations 0 " +
        nTilesParm + " " + averagingParm + " " + gpuParm;

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
    boolean initialized = false;
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
            if (!initialized)
            {
              progressMonitor().init(0, "", "", nBatches * nTiles);
              initialized = true;
            }
            progressMonitor().count(
                "Segmenting batch " + String.valueOf(batchIdx) + "/" +
                String.valueOf(nBatches) + ", tile " +
                String.valueOf(tileIdx) + "/" + String.valueOf(nTiles), 1);
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
      readyCancelButton().setText("Terminating...");
      readyCancelButton().setEnabled(false);
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

  public void runSegmentation(File file)
      throws IOException, InterruptedException {
    String gpuAttribute = new String();
    String gpuValue = new String();
    String selectedGPU = selectedGPUString();
    if (selectedGPU.contains("GPU ")) {
      gpuAttribute = "-gpu";
      gpuValue = selectedGPU.substring(selectedGPU.length() - 1);
    }
    else if (selectedGPU.contains("all")) {
      gpuAttribute = "-gpu";
      gpuValue = "all";
    }
    String averagingParm = new String();
    if (((String)_averagingComboBox.getSelectedItem()).equals("mirror"))
        averagingParm = "-average_mirror";
    else if (((String)_averagingComboBox.getSelectedItem()).equals("rotate"))
        averagingParm = "-average_rotate";

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
        nTilesAttribute + " " + nTilesValue + " " + averagingParm + " " +
        gpuAttribute + " " + gpuValue);
    ProcessBuilder pb;
    if (averagingParm.equals("")) {
      if (!gpuAttribute.equals(""))
          pb = new ProcessBuilder(
              commandString, "tiled_predict", "-infileH5",
              file.getAbsolutePath(), "-outfileH5", file.getAbsolutePath(),
              "-model", model().file.getAbsolutePath(), "-weights",
              weightsFileName(), "-iterations", "0",
              nTilesAttribute, nTilesValue, gpuAttribute,
              gpuValue);
      else
          pb = new ProcessBuilder(
              commandString, "tiled_predict", "-infileH5",
              file.getAbsolutePath(), "-outfileH5", file.getAbsolutePath(),
              "-model", model().file.getAbsolutePath(), "-weights",
              weightsFileName(), "-iterations", "0",
              nTilesAttribute, nTilesValue);
    }
    else {
      if (!gpuAttribute.equals(""))
          pb = new ProcessBuilder(
              commandString, "tiled_predict", "-infileH5",
              file.getAbsolutePath(), "-outfileH5", file.getAbsolutePath(),
              "-model", model().file.getAbsolutePath(), "-weights",
              weightsFileName(), "-iterations", "0",
              nTilesAttribute, nTilesValue, averagingParm, gpuAttribute,
              gpuValue);
      else
          pb = new ProcessBuilder(
              commandString, "tiled_predict", "-infileH5",
              file.getAbsolutePath(), "-outfileH5", file.getAbsolutePath(),
              "-model", model().file.getAbsolutePath(), "-weights",
              weightsFileName(), "-iterations", "0",
              nTilesAttribute, nTilesValue, averagingParm);
    }

    Process p = pb.start();

    BufferedReader stdOutput =
        new BufferedReader(new InputStreamReader(p.getInputStream()));
    BufferedReader stdError =
        new BufferedReader(new InputStreamReader(p.getErrorStream()));

    int exitStatus = -1;
    String line;
    String errorMsg = "";
    boolean initialized = false;
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
            if (!initialized)
            {
              progressMonitor().init(0, "", "", nBatches * nTiles);
              initialized = true;
            }
            progressMonitor().count(
                "Segmenting batch " + String.valueOf(batchIdx) + "/" +
                String.valueOf(nBatches) + ", tile " +
                String.valueOf(tileIdx) + "/" + String.valueOf(nTiles), 1);
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
      readyCancelButton().setText("Terminating...");
      readyCancelButton().setEnabled(false);
      p.destroy();
      throw e;
    }

    if (exitStatus != 0) {
      IJ.log(errorMsg);
      throw new IOException(
          "Error during segmentation: exit status " + exitStatus +
          "\nSee log for further details");
    }
  }

  public static void processHyperStack(String params)
      throws InterruptedException {
    final SegmentationJob job = new SegmentationJob();
    job.setImagePlus(WindowManager.getCurrentImage());
    if (job._imp == null) {
      IJ.noImage();
      return;
    }

    String[] parameterStrings = params.split(",");
    Map<String,String> parameters = new HashMap<String,String>();
    for (int i = 0; i < parameterStrings.length; i++)
        parameters.put(parameterStrings[i].split("=")[0],
                       parameterStrings[i].split("=")[1]);
    ModelDefinition model = new ModelDefinition();
    model.load(new File(parameters.get("modelFilename")));
    job.setModel(model);
    job.setWeightsFileName(parameters.get("weightsFilename"));
    job.model().setFromTilingParameterString(parameterStrings[2]);
    job.setGPUString(parameters.get("gpuId"));
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
        job.setSshSession(jsch.getSession(username, hostname, port));
        job.sshSession().setUserInfo(new MyUserInfo());

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
                  job.sshSession().setPassword(passwordAsBytes);
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

        job.sshSession().connect();
      }
      catch (JSchException e) {
        IJ.log("Macro call to SegmentationJob.processHyperStack aborted. " +
               "Could not establish SSH connection.");
        IJ.error("U-Net Segmentation", "Could not establish SSH connection.");
        return;
      }
    }
    job.setProcessFolder(parameters.get("processFolder"));
    job._keepOriginalCheckBox.setSelected(
        Boolean.valueOf(parameters.get("keepOriginal")));
    job._outputScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputScores")));
    job._outputSoftmaxScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputSoftmaxScores")));
    job.setInteractive(false);

    job.start();
    job.join();
    job.finish();
  }

  @Override
  public void run(String arg) {
    setImagePlus(WindowManager.getCurrentImage());
    if (_imp == null) {
      IJ.noImage();
      return;
    }
    start();
    try {
      join();
    }
    catch (InterruptedException e) {}
  }

  @Override
  public void run() {
    try {
      prepareParametersDialog();
      if (isInteractive() && !getParameters()) return;
      if (sshSession() != null) {
        progressMonitor().initNewTask("Uploading Model", 0.01f, 1);
        if (!isInteractive()) {
          model().remoteAbsolutePath =
              processFolder() + "/" + id() + "_model.h5";
          _createdRemoteFolders.addAll(
              Tools.put(
                  model().file, model().remoteAbsolutePath, sshSession(),
                  this));
          _createdRemoteFiles.add(model().remoteAbsolutePath);
        }

        _localTmpFile = File.createTempFile(id(), ".h5");
        _localTmpFile.delete();
        String remoteFileName = processFolder() + "/" + id() + ".h5";

        progressMonitor().initNewTask("Creating HDF5 blobs", 0.02f, 1);
        setImagePlus(
            Tools.saveHDF5Blob(
                _imp, _localTmpFile, this, _keepOriginalCheckBox.isSelected(),
                true));
        if (interrupted()) throw new InterruptedException();

        progressMonitor().initNewTask("Uploading HDF5 blobs", 0.1f, 1);
        _createdRemoteFolders.addAll(
            Tools.put(_localTmpFile, remoteFileName, sshSession(), this));
        _createdRemoteFiles.add(remoteFileName);
        if (interrupted()) throw new InterruptedException();

        progressMonitor().initNewTask("U-Net segmentation", 0.9f, 1);
        runSegmentation(remoteFileName, sshSession());
        if (interrupted()) throw new InterruptedException();

        progressMonitor().initNewTask("Downloading segmentation", 1.0f, 1);
        Tools.get(remoteFileName, _localTmpFile, sshSession(), this);
        if (interrupted()) throw new InterruptedException();
     }
      else {
        _localTmpFile = new File(processFolder() + "/" + id() + ".h5");

        progressMonitor().initNewTask("Creating HDF5 blobs", 0.02f, 1);
        setImagePlus(
            Tools.saveHDF5Blob(
                _imp, _localTmpFile, this, _keepOriginalCheckBox.isSelected(),
                true));
        if (interrupted()) throw new InterruptedException();

        progressMonitor().initNewTask("U-Net Segmentation", 1.0f, 1);
        runSegmentation(_localTmpFile);
        if (interrupted()) throw new InterruptedException();
      }
      setReady(true);
    }
    catch (InterruptedException e) {
      IJ.showMessage("Job " + id() + " canceled. Cleaning up.");
      abort();
    }
    catch (Exception e) {
      IJ.error("U-Net Segmentation", e.toString());
      abort();
    }
  }

};
