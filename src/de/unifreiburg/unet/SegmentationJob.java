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
import ij.process.ImageProcessor;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import ij.gui.PointRoi;

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
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.hdf5.HDF5FloatStorageFeatures;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;

public class SegmentationJob extends CaffeJob implements PlugIn {

  protected File _localTmpFile = null;

  protected ImagePlus _imp = null;

  protected final String[] _averagingModes = { "none", "mirror", "rotate" };
  protected JComboBox<String> _averagingComboBox =
      new JComboBox<String>(_averagingModes);
  protected JCheckBox _keepOriginalCheckBox = new JCheckBox(
      "Keep original", Prefs.get("unet.segmentation.keepOriginal", false));
  protected JCheckBox _outputScoresCheckBox = new JCheckBox(
      "Show scores", Prefs.get("unet.segmentation.outputScores", false));
  protected JCheckBox _outputSoftmaxScoresCheckBox = new JCheckBox(
      "Show softmax scores",
      Prefs.get("unet.segmentation.outputSoftmaxScores", false));

  public SegmentationJob() {
    super();
    setImagePlus(WindowManager.getCurrentImage());
  }

  public SegmentationJob(JobTableModel model) {
    super(model);
    setImagePlus(WindowManager.getCurrentImage());
  }

  public void setImagePlus(ImagePlus imp) {
    _imp = imp;
  }

  public ImagePlus image() {
    return _imp;
  }

  @Override
  public String imageName() {
    return (_imp != null) ? _imp.getTitle() : "N/A";
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
      if (jobTable() == null || !JobManager.instance().isVisible()) finish();
      else
          SwingUtilities.invokeLater(
              new Runnable() {
                @Override
                public void run() {
                  jobTable().updateJobDownloadEnabled(SegmentationJob.this);
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
      Thread finishThread =  new Thread() {
            @Override
            public void run() {
              try {
                loadSegmentationToImagePlus();
                if (Recorder.record) {
                  Recorder.setCommand(null);
                  String command =
                      "call('de.unifreiburg.unet.SegmentationJob." +
                      "processHyperStack', " +
                      "'modelFilename=" + model().file.getAbsolutePath() +
                      ",weightsFilename=" + weightsFileName() +
                      "," + model().getTilingParameterString() +
                      ",gpuId=" + selectedGPUString() +
                      "," + hostConfiguration().getMacroParameterString() +
                      ",processFolder=" + processFolder() +
                      ",average=" +
                      (String)_averagingComboBox.getSelectedItem() +
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
                showError("Could not load segmentation result", e);
              }
              finishJob();
            }
          };
      if (isInteractive()) finishThread.start();
      else finishThread.run();
    }
    catch (IllegalThreadStateException e) {}
  }

  @Override
  protected void createDialogElements() {

    super.createDialogElements();

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
  }

  @Override
  protected boolean checkParameters() throws InterruptedException {

    if (!super.checkParameters()) return false;

    int nChannels =
        (_imp.getType() == ImagePlus.COLOR_256 ||
         _imp.getType() == ImagePlus.COLOR_RGB) ? 3 : _imp.getNChannels();

    String caffe_unetBinary =
        Prefs.get("unet.caffe_unetBinary", "caffe_unet");

    ProcessResult res = null;

    try {
      if (sshSession() != null) {

        try {
          model().remoteAbsolutePath = processFolder() + id() + ".modeldef.h5";
          _createdRemoteFolders.addAll(
              new SftpFileIO(sshSession(), progressMonitor()).put(
                  model().file, model().remoteAbsolutePath));
          _createdRemoteFiles.add(model().remoteAbsolutePath);
          Prefs.set("unet.processfolder", processFolder());
        }
        catch (SftpException|JSchException e) {
          showError("Model upload failed.\nDo you have sufficient " +
                    "permissions to create the processing folder on " +
                    "the remote host?", e);
          return false;
        }
        catch (IOException e) {
          showError("Model upload failed. Could not read model file.", e);
          return false;
        }

        try {
          boolean weightsUploaded = false;
          do {
            String cmd =
                caffe_unetBinary + " check_model_and_weights_h5 -model \"" +
                model().remoteAbsolutePath + "\" -weights \"" +
                weightsFileName() + "\" -n_channels " + nChannels + " " +
                caffeGPUParameter();
            res = Tools.execute(cmd, sshSession(), progressMonitor());

            if (res.exitStatus != 0) {
              if (Tools.getCaffeError(res.cerr) > 0) {
                showMessage("Model check failed:\n" +
                            Tools.getCaffeErrorString(res.cerr));
                return false;
              }
              IJ.log(Tools.getCaffeErrorString(res.cerr));
            }

            if (res.exitStatus == 0 || weightsUploaded) break;

            if (!weightsUploaded) {
              int selectedOption = JOptionPane.showConfirmDialog(
                  _imp.getWindow(), "No compatible weights found at the " +
                  "given location on the backend server.\nDo you want " +
                  "to upload weights from the local machine now?",
                  "Upload weights?", JOptionPane.YES_NO_CANCEL_OPTION,
                  JOptionPane.QUESTION_MESSAGE);
              switch (selectedOption) {
              case JOptionPane.YES_OPTION: {
                File startFile =
                    (model() == null ||
                     model().file == null ||
                     model().file.getParentFile() == null) ?
                    new File(".") : model().file.getParentFile();
                JFileChooser f = new JFileChooser(startFile);
                f.setDialogTitle("Select trained U-Net weights");
                f.setFileFilter(
                    new FileNameExtensionFilter(
                        "*.h5 or *.caffemodel",
                        "h5", "H5", "caffemodel", "CAFFEMODEL"));
                f.setMultiSelectionEnabled(false);
                f.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int res2 = f.showDialog(
                    WindowManager.getActiveWindow(), "Select");
                if (res2 != JFileChooser.APPROVE_OPTION)
                    throw new InterruptedException("Aborted by user");
                try {
                  new SftpFileIO(sshSession(), progressMonitor()).put(
                      f.getSelectedFile(), weightsFileName());
                  weightsUploaded = true;
                }
                catch (SftpException e) {
                  res.exitStatus = 3;
                  res.shortErrorString =
                      "Upload failed.\nDo you have sufficient " +
                      "permissions to create a file at the given " +
                      "backend server path?";
                  res.cerr = e.getMessage();
                  res.cause = e;
                  break;
                }
                break;
              }
              case JOptionPane.NO_OPTION: {
                res.exitStatus = 2;
                res.shortErrorString = "Weight file selection required";
                res.cerr = "Weight file " + weightsFileName() + " not found";
                break;
              }
              case JOptionPane.CANCEL_OPTION:
              case JOptionPane.CLOSED_OPTION:
                throw new InterruptedException("Aborted by user");
              }
              if (res.exitStatus > 1) break;
            }
            else {
              IJ.log(res.cerr);
              res.shortErrorString = "Model/Weights check failed.";
              res.cerr = "See log for further details";
              break;
            }
          }
          while (res.exitStatus != 0);
        }
        catch (JSchException e) {
          res.exitStatus = 1;
          res.shortErrorString = "SSH connection error";
          res.cerr = e.getMessage();
          res.cause = e;
        }
        catch (IOException e) {
          res.exitStatus = 1;
          res.shortErrorString = "Input/Output error";
          res.cerr = e.getMessage();
          res.cause = e;
        }
      }
      else {
        if (!new File(weightsFileName()).exists())
        {
          showMessage(
              "The selected weight file does not exist.\n" +
              "Please select a .caffemodel.h5 file matching your model.");
          return false;
        }
        try {
          Vector<String> cmd = new Vector<String>();
          cmd.add(caffe_unetBinary);
          cmd.add("check_model_and_weights_h5");
          cmd.add("-model");
          cmd.add((sshSession() == null) ? model().file.getAbsolutePath() :
                  model().remoteAbsolutePath);
          cmd.add("-weights");
          cmd.add(weightsFileName());
          cmd.add("-n_channels");
          cmd.add(new Integer(nChannels).toString());
          if (!caffeGPUParameter().equals("")) {
            cmd.add(caffeGPUParameter().split(" ")[0]);
            cmd.add(caffeGPUParameter().split(" ")[1]);
          }
          res = Tools.execute(cmd, progressMonitor());
        }
        catch (IOException e) {
          res.exitStatus = 1;
          res.shortErrorString = "Input/Output error";
          res.cerr = e.getMessage();
          res.cause = e;
        }
      }
      if (res.exitStatus != 0) {
        // User decided to change weight file, so don't bother him with
        // additional message boxes
        if (res.exitStatus == 2) return false;
        IJ.log(res.cerr);
        showError(
            "Model/Weight check failed:\n" + res.shortErrorString, res.cause);
        return false;
      }

      Prefs.set("unet.segmentation.keepOriginal",
                _keepOriginalCheckBox.isSelected());
      Prefs.set("unet.segmentation.outputScores",
                _outputScoresCheckBox.isSelected());
      Prefs.set("unet.segmentation.outputSoftmaxScores",
                _outputSoftmaxScoresCheckBox.isSelected());
    }
    catch (JSchException e) {
      showError("SSH connection failed", e);
      return false;
    }

    return true;
  }

  private boolean getParameters() throws InterruptedException {

    if (_imp == null) setImagePlus(WindowManager.getCurrentImage());
    if (_imp == null) {
      showMessage("U-Net segmentation requires an open hyperstack to segment.");
      return false;
    }

    progressMonitor().push("Waiting for user input", 0.0f, 0.0f);

    do {
      _parametersDialog.setVisible(true);

      // Dialog was cancelled
      if (!_parametersDialog.isDisplayable())
          throw new InterruptedException("Dialog canceled");
    }
    while (!checkParameters());

    _parametersDialog.dispose();

    if (jobTable() != null) jobTable().fireTableDataChanged();

    progressMonitor().pop();

    return true;
  }

  private boolean parseCaffeOutputString(String msg, boolean initialized) {
    boolean init = initialized;
    Matcher matcher = Pattern.compile(
        "Processing batch ([0-9]+)/([0-9]+), tile ([0-9]+)/([0-9]+)").matcher(
            msg);
    if (!matcher.matches() || matcher.groupCount() < 4) return init;
    int batchIdx = Integer.parseInt(matcher.group(1));
    int nBatches = Integer.parseInt(matcher.group(2));
    int tileIdx = Integer.parseInt(matcher.group(3));
    int nTiles = Integer.parseInt(matcher.group(4));
    if (!init) {
      progressMonitor().init(0, "", "", nBatches * nTiles);
      init = true;
    }
    progressMonitor().count(
        "Segmenting batch " + batchIdx + "/" + nBatches + ", tile " +
        tileIdx + "/" + nTiles, 1);
    return init;
  }

  private void runSegmentation(String fileName)
      throws JSchException, IOException, InterruptedException {

    Vector<String> cmd = new Vector<String>();
    cmd.add(Prefs.get("unet.caffe_unetBinary", "caffe_unet"));
    cmd.add("tiled_predict");
    cmd.add("-infileH5");
    cmd.add(fileName);
    cmd.add("-outfileH5");
    cmd.add(fileName);
    cmd.add("-model");
    cmd.add((sshSession() == null) ? model().file.getAbsolutePath() :
            model().remoteAbsolutePath);
    cmd.add("-weights");
    cmd.add(weightsFileName());
    cmd.add("-iterations");
    cmd.add("0");
    String[] tilingParameters = model().getCaffeTilingParameter().split(
        "\\s");
    cmd.add(tilingParameters[0]);
    cmd.add(tilingParameters[1]);
    if (((String)_averagingComboBox.getSelectedItem()).equals("mirror"))
        cmd.add("-average_mirror");
    else if (((String)_averagingComboBox.getSelectedItem()).equals("rotate"))
        cmd.add("-average_rotate");
    if (selectedGPUString().contains("GPU ")) {
      cmd.add("-gpu");
      cmd.add(selectedGPUString().substring(selectedGPUString().length() - 1));
    }
    else if (selectedGPUString().contains("all")) {
      cmd.add("-gpu");
      cmd.add("all");
    }
    String cmdString = "";
    for (String cmdComponent : cmd) cmdString += cmdComponent + " ";
    IJ.log(cmdString);

    Channel channel = null;
    Process p = null;
    int pid = -1;
    BufferedReader stdOutput = null;
    BufferedReader stdError = null;
    if (sshSession() == null) {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      p = pb.start();
      pid = Tools.getPID(p);
      stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));
      stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
    }
    else {
      channel = sshSession().openChannel("exec");
      ((ChannelExec)channel).setCommand("echo $$ && exec " + cmdString);
      stdOutput = new BufferedReader(
          new InputStreamReader(channel.getInputStream()));
      stdError = new BufferedReader(
          new InputStreamReader(((ChannelExec)channel).getErrStream()));
      channel.connect();
    }

    int exitStatus = -1;
    String line = null;
    String outMsg = new String();
    String errorMsg = new String();
    boolean initialized = false;
    boolean firstLine = (sshSession() == null) ? false : true;
    try {
      while (true) {
        // Check for ready() to avoid thread blocking, then read
        // all available lines from the buffer and update progress
        while (stdOutput.ready()) {
          line = stdOutput.readLine();
          if (firstLine) {
            pid = Integer.parseInt(line);
            firstLine = false;
          }
          else outMsg += line + "\n";
          initialized = parseCaffeOutputString(line, initialized);
        }
        // Also read error stream to avoid stream overflow that leads
        // to process stalling
        while (stdError.ready()) {
          line = stdError.readLine();
          errorMsg += line + "\n";
          initialized = parseCaffeOutputString(line, initialized);
        }
        if (sshSession() != null) {
          if (channel.isClosed()) {
            if(stdOutput.ready() || stdError.ready()) continue;
            exitStatus = channel.getExitStatus();
            break;
          }
        }
        else {
          try {
            exitStatus = p.exitValue();
            break;
          }
          catch (IllegalThreadStateException e) {}
        }
        if (interrupted()) throw new InterruptedException();
        Thread.sleep(100);
      }
    }
    catch (InterruptedException e) {
      readyCancelButton().setText("Terminating...");
      readyCancelButton().setEnabled(false);
      if (pid != -1) {
        if (sshSession() != null)
            Tools.execute(
                "kill -2 " + pid, sshSession(), progressMonitor());
        else {
          Vector<String> cmd2 = new Vector<String>();
          cmd2.add("kill");
          cmd2.add("-2");
          cmd2.add(new Integer(pid).toString());
          Tools.execute(cmd2, progressMonitor());
        }
      }
      if (sshSession() != null) channel.disconnect();
      else p.destroy();
      throw e;
    }
    if (exitStatus != 0) {
      IJ.log(errorMsg);
      throw new IOException(
          "Error during segmentation: exit status " + exitStatus + "\n" +
          Tools.getCaffeErrorString(errorMsg) + "\n" +
          "See log for further details");
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
    for (int i = 0; i < parameterStrings.length; i++) {
      String[] param = parameterStrings[i].split("=");
      parameters.put(param[0], (param.length > 1) ? param[1] : "");
    }
    ModelDefinition model = new ModelDefinition(job);
    model.load(new File(parameters.get("modelFilename")));
    job.setModel(model);
    job.setWeightsFileName(parameters.get("weightsFilename"));
    job.model().setFromTilingParameterString(parameterStrings[2]);
    job.setGPUString(parameters.get("gpuId"));
    try
    {
      job.hostConfiguration().connectFromParameterMap(parameters);
    }
    catch (JSchException e) {
      IJ.log("Macro call to SegmentationJob.processHyperStack aborted. " +
             "Could not establish SSH connection.");
      IJ.error("U-Net Segmentation", "Could not establish SSH connection.");
      return;
    }
    job.setProcessFolder(parameters.get("processFolder"));
    job._keepOriginalCheckBox.setSelected(
        Boolean.valueOf(parameters.get("keepOriginal")));
    job._outputScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputScores")));
    job._outputSoftmaxScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputSoftmaxScores")));
    job.setInteractive(false);

    // Run blocking on current thread
    job.run();
  }

  @Override
  public void run(String arg) {
    JobManager.instance().addJob(this);
    start();
  }

  @Override
  public void run() {
    if (WindowManager.getCurrentImage() == null) {
      IJ.noImage();
      return;
    }
    setImagePlus(WindowManager.getCurrentImage());
    try {
      progressMonitor().count("Segmentation", 0);

      prepareParametersDialog();
      if (isInteractive() && !getParameters()) return;

      progressMonitor().push(
          "Creating Caffe blobs", 0.0f, (sshSession() != null) ? 0.03f : 0.09f);

      TrainingSample t = new TrainingSample(_imp);
      t.createDataBlob(model(), progressMonitor());
      if (t.dataBlob() != _imp) {
        if (!_keepOriginalCheckBox.isSelected()) {
          _imp.changes = false;
          _imp.close();
        }
        setImagePlus(t.dataBlob());
        _imp.show();
        _imp.setDisplayRange(0, 1);
        _imp.updateAndDraw();
      }

      String remoteFileName = null;
      if (sshSession() == null)
          _localTmpFile = new File(processFolder() + id() + ".h5");
      else {
        _localTmpFile = File.createTempFile(id(), ".h5");
        _localTmpFile.delete();
        remoteFileName = processFolder() + id() + ".h5";
      }

      if (interrupted()) throw new InterruptedException();
      progressMonitor().pop();
      progressMonitor().push(
          "Saving Caffe blobs", (sshSession() != null) ? 0.03f : 0.09f,
          (sshSession() != null) ? 0.04f : 0.1f);

      _createdLocalFiles.addAll(
          t.saveBlobs(_localTmpFile, model(), progressMonitor()));

      if (interrupted()) throw new InterruptedException();
      progressMonitor().pop();

      if (sshSession() != null) {
        progressMonitor().push("Uploading Caffe blobs", 0.04f, 0.09f);

        _createdRemoteFolders.addAll(
            new SftpFileIO(sshSession(), progressMonitor()).put(
                _localTmpFile, remoteFileName));
        _createdRemoteFiles.add(remoteFileName);

        if (interrupted()) throw new InterruptedException();
        progressMonitor().pop();
        progressMonitor().push("Uploading Model", 0.09f, 0.1f);

        SftpFileIO sftp = new SftpFileIO(sshSession(), progressMonitor());
        if (!isInteractive()) {
          model().remoteAbsolutePath =
              processFolder() + id() + ".modeldef.h5";
          _createdRemoteFolders.addAll(
              new SftpFileIO(sshSession(), progressMonitor()).put(
                  model().file, model().remoteAbsolutePath));
          _createdRemoteFiles.add(model().remoteAbsolutePath);
        }
        progressMonitor().pop();
      }

      progressMonitor().push(
          "U-Net segmentation", 0.1f, (sshSession() != null) ? 0.9f : 1.0f);
      runSegmentation(
          (sshSession() != null) ? remoteFileName :
          _localTmpFile.getAbsolutePath());
      if (interrupted()) throw new InterruptedException();

      if (sshSession() != null) {
        progressMonitor().pop();
        progressMonitor().push("Downloading segmentation", 0.9f, 1.0f);
        new SftpFileIO(sshSession(), progressMonitor()).get(
            remoteFileName, _localTmpFile);
      }

      if (interrupted()) throw new InterruptedException();
      progressMonitor().end();
      setReady(true);
    }
    catch (InterruptedException e) {
      abort();
    }
    catch (TrainingSampleException e) {
      showError("Invalid Training Sample", e);
      abort();
    }
    catch (JSchException e) {
      IJ.error(id(), "Connection to remote host failed:\n" + e);
      abort();
    }
    catch (SftpException e) {
      IJ.error(id(), "File transfer failed:\n" + e);
      abort();
    }
    catch (IOException e) {
      IJ.error(id(), "Input/Output error:\n" + e);
      abort();
    }
  }

  protected void loadSegmentationToImagePlus()
      throws HDF5Exception, IOException {

    File file = _localTmpFile;
    boolean outputScores = _outputScoresCheckBox.isSelected();
    boolean outputSoftmaxScores = _outputSoftmaxScoresCheckBox.isSelected();
    boolean generateMarkers = (this instanceof DetectionJob);

    progressMonitor().reset();
    progressMonitor().push("Creating visualization", 0.0f, 1.0f);

    boolean computeSoftmaxScores = outputSoftmaxScores || generateMarkers;

    IHDF5Reader reader =
        HDF5Factory.configureForReading(file.getAbsolutePath()).reader();
    List<String> outputs = reader.getGroupMembers("/");

    int dsIdx = 1;
    for (String dsName : outputs) {

      progressMonitor().push(
          "Creating visualization for " + dsName,
          (float)(dsIdx - 1) / (float)outputs.size(),
          (float)dsIdx / (float)outputs.size());

      String title = imageName() + " - " + dsName;
      HDF5DataSetInformation dsInfo =
          reader.object().getDataSetInformation(dsName);
      int nDims    = dsInfo.getDimensions().length - 2;
      int nFrames  = (int)dsInfo.getDimensions()[0];
      int nClasses = (int)dsInfo.getDimensions()[1];
      int nLevs    = (nDims == 2) ? 1 : (int)dsInfo.getDimensions()[2];
      int nRows    = (int)dsInfo.getDimensions()[2 + ((nDims == 2) ? 0 : 1)];
      int nCols    = (int)dsInfo.getDimensions()[3 + ((nDims == 2) ? 0 : 1)];

      ImagePlus impScores = null;
      if (outputScores) {
        impScores = IJ.createHyperStack(
            title, nCols, nRows, nClasses, nLevs, nFrames, 32);
        impScores.setDisplayMode(IJ.GRAYSCALE);
        impScores.setCalibration(_imp.getCalibration().copy());
      }

      ImagePlus impSoftmaxScores = null;
      if (computeSoftmaxScores) {
        impSoftmaxScores = IJ.createHyperStack(
            title + " (softmax)", nCols, nRows, nClasses, nLevs, nFrames, 32);
        impSoftmaxScores.setDisplayMode(IJ.GRAYSCALE);
        impSoftmaxScores.setCalibration(_imp.getCalibration().copy());
      }

      ImagePlus impClassification = IJ.createHyperStack(
          title + " (segmentation)", nCols, nRows, 1, nLevs, nFrames, 16);
      impClassification.setDisplayMode(IJ.GRAYSCALE);
      impClassification.setCalibration(_imp.getCalibration().copy());

      int[] blockDims = (nDims == 2) ?
          (new int[] { 1, 1, nRows, nCols }) :
          (new int[] { 1, 1, 1, nRows, nCols });
      long[] blockIdx = (nDims == 2) ?
          (new long[] { 0, 0, 0, 0 }) : (new long[] { 0, 0, 0, 0, 0 });

      int nOperations = nFrames * nLevs * nClasses;
      if (generateMarkers) nOperations += 4 * nFrames * nLevs * (nClasses - 1);
      progressMonitor().init(nOperations);
      dsIdx++;

      for (int t = 0; t < nFrames; ++t) {
        blockIdx[0] = t;
        for (int z = 0; z < nLevs; ++z) {
          if (nDims == 3) blockIdx[2] = z;
          float[] maxScore = new float[nRows * nCols];
          float[] expScoreSum =
              computeSoftmaxScores ? new float[nRows * nCols] : null;
          ImageProcessor ipC = impClassification.getStack().getProcessor(
              impClassification.getStackIndex(1, z + 1, t + 1));
          short[] maxIndex = (short[])ipC.getPixels();
          int c = 0;
          blockIdx[1] = c;
          progressMonitor().count(
              "Classification t=" + (t + 1) + "/" + nFrames +
              ", z=" + (z + 1) + "/" + nLevs + ", class=" + c + "/" +
              (nClasses - 1), 1);
          float[] score = reader.float32().readMDArrayBlock(
              dsName, blockDims, blockIdx).getAsFlatArray();
          if (outputScores)
              System.arraycopy(
                  score, 0,
                  impScores.getStack().getProcessor(
                      impScores.getStackIndex(c + 1, z + 1, t + 1)).getPixels(),
                  0, nRows * nCols);
          if (computeSoftmaxScores) {
            float[] smscores =
                (float[])impSoftmaxScores.getStack().getProcessor(
                    impSoftmaxScores.getStackIndex(
                        c + 1, z + 1, t + 1)).getPixels();
            for (int i = 0; i < nRows * nCols; ++i) {
              smscores[i] = (float)Math.exp((double)score[i]);
              expScoreSum[i] = smscores[i];
            }
          }
          System.arraycopy(score, 0, maxScore, 0, nRows * nCols);
          Arrays.fill(maxIndex, (short) c);
          ++c;
          for (; c < nClasses; ++c) {
            blockIdx[1] = c;
            progressMonitor().count(
                "Classification t=" + (t + 1) + "/" + nFrames +
                ", z=" + (z + 1) + "/" + nLevs + ", class=" + c + "/" +
                (nClasses - 1), 1);
            score = reader.float32().readMDArrayBlock(
                dsName, blockDims, blockIdx).getAsFlatArray();
            if (outputScores)
                System.arraycopy(
                    score, 0, impScores.getStack().getProcessor(
                        impScores.getStackIndex(
                            c + 1, z + 1, t + 1)).getPixels(),
                    0, nRows * nCols);
            if (computeSoftmaxScores) {
              float[] smscores =
                  (float[])impSoftmaxScores.getStack().getProcessor(
                  impSoftmaxScores.getStackIndex(
                      c + 1, z + 1, t + 1)).getPixels();
              for (int i = 0; i < nRows * nCols; ++i) {
                smscores[i] = (float)Math.exp((double)score[i]);
                expScoreSum[i] += smscores[i];
              }
            }
            for (int i = 0; i < nRows * nCols; ++i) {
              if (score[i] > maxScore[i]) {
                maxScore[i] = score[i];
                maxIndex[i] = (short) c;
              }
            }
          }
          if (computeSoftmaxScores) {
            for (c = 0; c < nClasses; ++c) {
              float[] smscores =
                  (float[])impSoftmaxScores.getStack().getProcessor(
                      impSoftmaxScores.getStackIndex(
                          c + 1, z + 1, t + 1)).getPixels();
              for (int i = 0; i < nRows * nCols; ++i)
                  smscores[i] /= expScoreSum[i];
            }
          }
        }
      }

      if (outputScores) {
        for (int i = 0; i < impScores.getStackSize(); ++i) {
          impScores.setSlice(i + 1);
          impScores.resetDisplayRange();
        }
        impScores.setSlice(1);
        impScores.show();
      }
      if (outputSoftmaxScores) {
        for (int i = 0; i < impSoftmaxScores.getStackSize(); ++i) {
          impSoftmaxScores.setSlice(i + 1);
          impSoftmaxScores.setDisplayRange(0.0, 1.0);
        }
        impSoftmaxScores.setSlice(1);
        impSoftmaxScores.show();
      }
      if (impClassification.getStackSize() > 1) {
        for (int i = 0; i < impClassification.getStackSize(); ++i) {
          impClassification.setSlice(i + 1);
          impClassification.resetDisplayRange();
        }
        impClassification.setSlice(1);
      }
      else impClassification.resetDisplayRange();
      impClassification.show();

      if (generateMarkers) {

        // Create multi-channel image of per class binary masks
        ImagePlus impMCClassification = IJ.createHyperStack(
            title + " (classes)", nCols, nRows, nClasses - 1,
            nLevs, nFrames, 8);
        impMCClassification.setCalibration(_imp.getCalibration().copy());

        for (int t = 0; t < nFrames; ++t) {
          for (int z = 0; z < nLevs; ++z) {
            short[] cl = (short[])impClassification.getStack().getProcessor(
                impClassification.getStackIndex(1, z + 1, t + 1)).getPixels();
            byte[][] mccl = new byte[nClasses - 1][];
            for (int c = 0; c < nClasses - 1; ++c)
            {
              progressMonitor().count(
                  "Generating masks t=" + (t + 1) + "/" + nFrames +
                  ", z=" + (z + 1) + "/" + nLevs + ", class=" + (c + 1) + "/" +
                  (nClasses - 1), 1);
              ImageProcessor impMccl =
                  impMCClassification.getStack().getProcessor(
                      impMCClassification.getStackIndex(c + 1, z + 1, t + 1));
              impMccl.setValue(0);
              impMccl.fill();
              mccl[c] = (byte[])impMccl.getPixels();
            }
            for (int i = 0; i < nRows * nCols; ++i)
                if (cl[i] != 0) mccl[cl[i] - 1][i] = (byte)255;
          }
        }

        // Connected component labeling
        progressMonitor().count("Connected component labeling", 0);
        ConnectedComponentLabeling.ConnectedComponents connComps =
            ConnectedComponentLabeling.label(
                impMCClassification,
                ConnectedComponentLabeling.SIMPLE_NEIGHBORHOOD,
                progressMonitor());

        // Compute centers of mass of connected components
        float[][][] centerPosUm = new float[nFrames * (nClasses - 1)][][];
        float[][] weightSum = new float[nFrames * (nClasses - 1)][];
        int[][] nPixels = new int[nFrames * (nClasses - 1)][];
        for (int i = 0; i < nFrames * (nClasses - 1); ++i) {
          centerPosUm[i] = new float[connComps.nComponents[i]][nDims];
          weightSum[i] = new float[connComps.nComponents[i]];
          nPixels[i] = new int[connComps.nComponents[i]];
          for (int j = 0; j < connComps.nComponents[i]; ++j) {
            for (int d = 0; d < nDims; ++d) centerPosUm[i][j][d] = 0.0f;
            weightSum[i][j] = 0.0f;
            nPixels[i][j] = 0;
          }
        }
        int[] labels = (int[])connComps.labels.data();
        double[] elSize = connComps.labels.elementSizeUm();
        int lblIdx = 0;
        for (int t = 0; t < nFrames; ++t) {
          for (int c = 0; c < nClasses - 1; ++c) {
            float[][] cPosUm = centerPosUm[t * (nClasses - 1) + c];
            float[] ws = weightSum[t * (nClasses - 1) + c];
            int[] np = nPixels[t * (nClasses - 1) + c];
            for (int z = 0; z < nLevs; ++z) {
              progressMonitor().count(
                  "Computing positions t=" + (t + 1) + "/" + nFrames +
                  ", z=" + (z + 1) + "/" + nLevs + ", class=" + (c + 1) + "/" +
                  (nClasses - 1), 1);
              float[] smscore = (float[])
                  impSoftmaxScores.getStack().getProcessor(
                      impSoftmaxScores.getStackIndex(
                          c + 2, z + 1, t + 1)).getPixels();
              int smIdx = 0;
              for (int y = 0; y < nRows; ++y) {
                for (int x = 0; x < nCols; ++x, ++lblIdx, ++smIdx) {
                  if (labels[lblIdx] == 0) continue;
                  if (nDims == 2) {
                    cPosUm[labels[lblIdx] - 1][0] +=
                        smscore[smIdx] * y * elSize[0];
                    cPosUm[labels[lblIdx] - 1][1] +=
                        smscore[smIdx] * x * elSize[1];
                  }
                  else {
                    cPosUm[labels[lblIdx] - 1][0] +=
                        smscore[smIdx] * z * elSize[0];
                    cPosUm[labels[lblIdx] - 1][1] +=
                        smscore[smIdx] * y * elSize[1];
                    cPosUm[labels[lblIdx] - 1][2] +=
                        smscore[smIdx] * x * elSize[2];
                  }
                  ws[labels[lblIdx] - 1] += smscore[smIdx];
                  np[labels[lblIdx] - 1]++;
                }
              }
            }
          }
        }

        Overlay overlay = new Overlay();
        ResultsTable table = new ResultsTable();
        int volIdx = 0;
        for (int t = 0; t < nFrames; ++t) {
          for (int c = 0; c < nClasses - 1; ++c, ++volIdx) {
            PointRoi[] detections = new PointRoi[nLevs];
            for (int j = 0; j < connComps.nComponents[volIdx]; ++j) {
              table.incrementCounter();
              table.addValue("frame", t + 1);
              for (int d = 0; d < nDims; ++d)
                  centerPosUm[volIdx][j][d] /= weightSum[volIdx][j];
              if (nDims == 2) {
                table.addValue("x [µm]", centerPosUm[volIdx][j][1]);
                table.addValue("y [µm]", centerPosUm[volIdx][j][0]);
                if (detections[0] == null) {
                  detections[0] = new PointRoi(
                      centerPosUm[volIdx][j][1] / elSize[1],
                      centerPosUm[volIdx][j][0] / elSize[0]);
                  detections[0].setPosition(t + 1);
                }
                else detections[0].addPoint(
                    centerPosUm[volIdx][j][1] / elSize[1],
                    centerPosUm[volIdx][j][0] / elSize[0]);
              }
              else {
                table.addValue("x [µm]", centerPosUm[volIdx][j][2]);
                table.addValue("y [µm]", centerPosUm[volIdx][j][1]);
                table.addValue("z [µm]", centerPosUm[volIdx][j][0]);
                int z = (int)Math.round(centerPosUm[volIdx][j][0] / elSize[0]);
                if (z < 0) z = 0;
                if (z >= nLevs) z = nLevs - 1;
                if (detections[z] == null) {
                  detections[z] = new PointRoi(
                      centerPosUm[volIdx][j][2] / elSize[2],
                      centerPosUm[volIdx][j][1] / elSize[1]);
                  if (nFrames > 1)
                      detections[z].setPosition(1, z + 1, t + 1);
                  else
                      detections[z].setPosition(z + 1);
                }
                else detections[z].addPoint(
                    centerPosUm[volIdx][j][2] / elSize[2],
                    centerPosUm[volIdx][j][1] / elSize[1]);
              }
              if (model().classNames != null)
                  table.addValue("class", model().classNames[c + 1]);
              else table.addValue("class", c + 1);
              table.addValue(
                  "confidence", weightSum[volIdx][j] / nPixels[volIdx][j]);
            }
            for (int z = 0; z < nLevs; ++z) {
              if (detections[z] != null) overlay.add(
                  detections[z], (model().classNames != null) ?
                  model().classNames[c + 1] : ("Class " + (c + 1)));
            }
          }
        }

        table.show(title + " (detections)");
        impClassification.setOverlay(overlay);
      }
      progressMonitor().pop(); // Process dataset
    }
    reader.close();
    progressMonitor().end();
  }

};
