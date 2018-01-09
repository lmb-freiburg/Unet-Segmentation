import ij.IJ;
import ij.Prefs;
import ij.WindowManager;
import ij.process.ImageProcessor;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.gui.Plot;

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.GroupLayout;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JFormattedTextField;
import javax.swing.text.NumberFormatter;
import javax.swing.JList;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JSplitPane;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Vector;

import java.text.DecimalFormat;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import caffe.Caffe;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat.Parser;
import com.google.protobuf.TextFormat;

public class UnetFinetuneJob extends UnetJob implements PlugIn {

  private final ImagePlusListView _trainFileList = new ImagePlusListView();
  private final ImagePlusListView _validFileList = new ImagePlusListView();
  private final JFormattedTextField _learningRateTextField =
      new JFormattedTextField(
          new NumberFormatter(new DecimalFormat("0.###E0")));
  private final JTextField _outfileTextField = new JTextField(
      Prefs.get("unet_finetuning.outfile", "finetuned.caffemodel.h5"));
  private final JSpinner _iterationsSpinner =
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet_finetuning.iterations", 1000),
              1, (int)Integer.MAX_VALUE, 1));

  private boolean _trainFromScratch = false;

  @Override
  protected void setReady(boolean ready) {
    if (ready) finish();
  }

  @Override
  public void finish() {
    if (_finished) return;
    cleanUp();
  }

  @Override
  public void prepareParametersDialog() {

    super.prepareParametersDialog();

    // Create Train/Test split Configurator
    int[] ids = WindowManager.getIDList();
    for (int i = 0; i < ids.length; i++)
        if (WindowManager.getImage(ids[i]).getOverlay() != null)
            ((DefaultListModel<ImagePlus>)_trainFileList.getModel()).addElement(
                WindowManager.getImage(ids[i]));

    JPanel trainImagesPanel = new JPanel(new BorderLayout());
    JLabel trainImagesLabel = new JLabel("Train images");
    trainImagesPanel.add(trainImagesLabel, BorderLayout.NORTH);
    JScrollPane trainScroller = new JScrollPane(_trainFileList);
    trainScroller.setMinimumSize(new Dimension(100, 50));
    trainImagesPanel.add(trainScroller, BorderLayout.CENTER);

    JPanel validImagesPanel = new JPanel(new BorderLayout());
    JLabel validImagesLabel = new JLabel("Validation images");
    validImagesPanel.add(validImagesLabel, BorderLayout.NORTH);
    JScrollPane validScroller = new JScrollPane(_validFileList);
    validScroller.setMinimumSize(new Dimension(100, 50));
    validImagesPanel.add(validScroller, BorderLayout.CENTER);

    JSplitPane trainValidPane = new JSplitPane(
        JSplitPane.HORIZONTAL_SPLIT, trainImagesPanel, validImagesPanel);

    JLabel learningRateLabel = new JLabel("Learning rate:");
    _learningRateTextField.setValue(
        (Double)Prefs.get("unet_finetuning.base_learning_rate", 1e-4));
    _learningRateTextField.setToolTipText(
        "Learning rate of the optimizer. You may use scientific notation, " +
        "(e.g. 1E-4 = 0.0001, note the capital E)");

    JLabel iterationsLabel = new JLabel("Iterations:");
    _iterationsSpinner.setToolTipText("The number of training iterations");

    JLabel outfileLabel = new JLabel("Output file:");
    _outfileTextField.setToolTipText(
        "Finetuned weights will be stored to this file");
    final JButton outfileChooseButton;
    if (UIManager.get("FileView.directoryIcon") instanceof Icon)
        outfileChooseButton = new JButton(
            (Icon)UIManager.get("FileView.directoryIcon"));
    else outfileChooseButton = new JButton("...");
    int marginTop = (int) Math.ceil(
        (outfileChooseButton.getPreferredSize().getHeight() -
         _outfileTextField.getPreferredSize().getHeight()) / 2.0);
    int marginBottom = (int) Math.floor(
        (outfileChooseButton.getPreferredSize().getHeight() -
         _outfileTextField.getPreferredSize().getHeight()) / 2.0);
    Insets insets = outfileChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    outfileChooseButton.setMargin(insets);
    outfileChooseButton.setToolTipText("Select output file name");

    _horizontalDialogLayoutGroup
        .addComponent(trainValidPane)
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(learningRateLabel)
                .addComponent(iterationsLabel)
                .addComponent(outfileLabel))
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(_learningRateTextField)
                .addComponent(_iterationsSpinner)
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_outfileTextField)
                    .addComponent(outfileChooseButton))));
    _verticalDialogLayoutGroup
        .addComponent(trainValidPane)
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(learningRateLabel)
            .addComponent(_learningRateTextField))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(iterationsLabel)
            .addComponent(_iterationsSpinner))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(outfileLabel)
            .addComponent(_outfileTextField)
            .addComponent(outfileChooseButton));

    outfileChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFolder = new File(_outfileTextField.getText());
            JFileChooser f = new JFileChooser(startFolder);
            f.setDialogTitle("Select output file name");
            f.setFileFilter(
                new FileNameExtensionFilter("HDF5 files", "h5", "H5"));
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _outfileTextField.setText(
                f.getSelectedFile().getAbsolutePath());
          }});

    // Finalize the dialog
    _parametersDialog.pack();
    _parametersDialog.setMinimumSize(
        _parametersDialog.getPreferredSize());
    _parametersDialog.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE,
            _parametersDialog.getPreferredSize().height));
    _parametersDialog.setLocationRelativeTo(WindowManager.getActiveWindow());
    trainValidPane.setDividerLocation(0.5);
  }

  public boolean getParameters() {
    if (WindowManager.getImageTitles().length == 0) {
      IJ.noImage();
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

      if (_trainFileList.getModel().getSize() == 0) {
        IJ.error("U-Net finetuning requires at least one training image.");
        dialogOK = false;
        continue;
      }

      int nChannels = -1;
      for (Object obj : ((DefaultListModel<ImagePlus>)
                         _trainFileList.getModel()).toArray()) {
        ImagePlus imp = (ImagePlus)obj;
        int nc = (imp.getType() == ImagePlus.COLOR_256 ||
                  imp.getType() == ImagePlus.COLOR_RGB) ? 3 :
            imp.getNChannels();
        if (nChannels == -1) nChannels = nc;
        if (nc != nChannels) {
          IJ.error("U-Net finetuning requires that all training and " +
                   "validation images have the same number of channels.");
          dialogOK = false;
          break;
        }
      }
      if (!dialogOK) continue;

      for (Object obj : ((DefaultListModel<ImagePlus>)
                         _validFileList.getModel()).toArray()) {
        ImagePlus imp = (ImagePlus)obj;
        int nc = (imp.getType() == ImagePlus.COLOR_256 ||
                  imp.getType() == ImagePlus.COLOR_RGB) ? 3 :
            imp.getNChannels();
        if (nc != nChannels) {
          IJ.error("U-Net finetuning requires that all training and " +
                   "validation images have the same number of channels.");
          dialogOK = false;
          break;
        }
      }
      if (!dialogOK) continue;

      // Check whether caffe binary exists and is executable
      ProcessResult res = null;
      if (_sshSession == null) {
        try {
          Vector<String> cmd = new Vector<String>();
          cmd.add(Prefs.get("unet_finetuning.caffeBinary", "caffe"));
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
          String cmd = Prefs.get("unet_finetuning.caffeBinary", "caffe");
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
            WindowManager.getActiveWindow(), "caffe was not found.\n" +
            "Please specify your caffe binary\n",
            Prefs.get("unet_finetuning.caffeBinary", "caffe"));
        if (caffePath == null) {
          cleanUp();
          return false;
        }
        if (caffePath.equals(""))
            Prefs.set("unet_finetuning.caffeBinary", "caffe");
        else Prefs.set("unet_finetuning.caffeBinary", caffePath);
        dialogOK = false;
        continue;
      }

      // Check for correct model and weight combination
      if (_sshSession != null) {
        model().remoteAbsolutePath =
            processFolder() + "/" + id() + "-modeldef.h5";
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
          String cmd =
              Prefs.get("unet_segmentation.caffeBinary", "caffe_unet") +
              " check_model_and_weights_h5 -model \"" +
              model().remoteAbsolutePath + "\" -weights \"" +
              weightsFileName() + "\" -n_channels " + nChannels + " " +
              caffeGPUParameter();
          res = UnetTools.execute(cmd, _sshSession, this);
          if (res.exitStatus != 0) {
            int selectedOption = JOptionPane.showConfirmDialog(
                WindowManager.getActiveWindow(),
                "No compatible pre-trained weights found at the given " +
                "location on the backend server.\n" +
                "Do you want to train from scratch?",
                "Start new Training?", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            switch (selectedOption) {
            case JOptionPane.YES_OPTION:
              _trainFromScratch = true;
              res.exitStatus = 0;
              break;
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
          }
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
          cmd.add("-n_channels");
          cmd.add(new Integer(nChannels).toString());
          if (!caffeGPUParameter().equals("")) {
            cmd.add(caffeGPUParameter().split(" ")[0]);
            cmd.add(caffeGPUParameter().split(" ")[1]);
          }
          res = UnetTools.execute(cmd, this);
          if (res.exitStatus != 0) {
            int selectedOption = JOptionPane.showConfirmDialog(
                WindowManager.getActiveWindow(),
                "No compatible pre-trained weights found at the given " +
                "location.\n" +
                "Do you want to train from scratch?", "Start new Training?",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            switch (selectedOption) {
            case JOptionPane.YES_OPTION:
              _trainFromScratch = true;
              res.exitStatus = 0;
              break;
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

    Prefs.set("unet_finetuning.base_learning_rate",
              (Double)_learningRateTextField.getValue());
    Prefs.set("unet_finetuning.outfile", _outfileTextField.getText());
    Prefs.set("unet_finetuning.iterations",
              (Integer)_iterationsSpinner.getValue());

    _parametersDialog.dispose();

    if (_jobTableModel != null) _jobTableModel.fireTableDataChanged();

    return true;
  }

  private void parseCaffeOutputString(
      String msg, ImagePlus lossImage, double[] x, double[] yTrain,
      double[] yValid) {
    int idx = -1;
    String line = null;
    while (msg.length() > 0) {
      if ((idx = msg.indexOf('\n')) != -1) {
        line = msg.substring(0, idx);
        msg = msg.substring(idx + 1);
      }
      else {
        line = msg;
        msg = "";
      }
      if (line.matches("^.*Iteration [0-9]+, loss = .*$")) {
        int iter = Integer.valueOf(line.split("Iteration ")[1].split(",")[0]);
        double loss = Double.valueOf(line.split("loss = ")[1]);

        // Update loss plot
        yTrain[iter] = loss;
        Plot plot = new Plot(
            "Finetuning Evolution", "Iteration", "Loss", x, yTrain);
        plot.setLimits(0.0, (double)(x.length - 1), 0.0, Double.NaN);
        ImageProcessor plotProcessor = plot.getProcessor();
        lossImage.setProcessor(null, plotProcessor);
        lossImage.updateAndDraw();

        // Update progress
        setTaskProgress(
            "Finetuning iteration " + iter + "/" + (x.length - 1) + " loss = " +
            loss, iter, x.length - 1);
        setProgress(
            (int) (_taskProgressMin + (float)(iter) / (float)(x.length - 1) *
                   (_taskProgressMax - _taskProgressMin)));
      }
    }
  }

  public void runUnetFinetuning()
      throws JSchException, IOException, InterruptedException {
    _taskStatus.isIndeterminate = true;
    setTaskProgress("Initializing U-Net", 0, 0);

    // Prepare caffe call
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
    String weightsAttribute = "";
    String weightsValue = "";
    if (!_trainFromScratch) {
      weightsAttribute = "-weights";
      weightsValue = weightsFileName();
    }
    String commandString = Prefs.get("unet_finetuning.caffeBinary", "caffe");
    String commandLineString =
        commandString + " train -solver " +
        model().solverPrototxtAbsolutePath + " " + weightsAttribute + " " +
        weightsValue + " " + gpuAttribute + " " + gpuValue;
    IJ.log(commandLineString);

    int nIter = (Integer)_iterationsSpinner.getValue();

    // Set up ImagePlus for plotting the loss curves
    ImagePlus plotImage = null;
    double[] x = new double[nIter + 1];
    double[] yTrain = new double[nIter + 1];
    double[] yValid = new double[nIter + 1];
    for (int i = 0; i <= nIter; i++) {
      x[i] = i + 1;
      yTrain[i] = Double.NaN;
      yValid[i] = Double.NaN;
    }
    {
      Plot plot = new Plot(
          "Finetuning " + id(), "Iteration", "Loss", x, yTrain);
      plot.setLimits(0.0, (double)nIter, 0.0, Double.NaN);
      ImageProcessor plotProcessor = plot.getProcessor();
      plotImage = new ImagePlus("Finetuning " + id(), plotProcessor);
      plotImage.setProcessor(null, plotProcessor);
      plotImage.show();
    }

    Channel channel = null;
    Process p = null;
    BufferedReader stdOutput = null;
    BufferedReader stdError = null;
    if (_sshSession == null)
    {
      ProcessBuilder pb = null;
      if (gpuAttribute.equals("")) {
        if (weightsAttribute.equals(""))
            pb = new ProcessBuilder(
                commandString, "train",
                "-solver", model().solverPrototxtAbsolutePath);
        else
            pb = new ProcessBuilder(
                commandString, "train",
                "-solver", model().solverPrototxtAbsolutePath,
                weightsAttribute, weightsValue);
      }
      else {
        if (weightsAttribute.equals(""))
            pb = new ProcessBuilder(
                commandString, "train",
                "-solver", model().solverPrototxtAbsolutePath,
                gpuAttribute, gpuValue);
        else
            pb = new ProcessBuilder(
                commandString, "train",
                "-solver", model().solverPrototxtAbsolutePath,
                weightsAttribute, weightsValue,
                gpuAttribute, gpuValue);
      }
      p = pb.start();
      stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));
      stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
    }
    else {
      channel = _sshSession.openChannel("exec");
      ((ChannelExec)channel).setCommand(commandLineString);
      stdOutput =
          new BufferedReader(new InputStreamReader(channel.getInputStream()));
      stdError =
          new BufferedReader(
              new InputStreamReader(((ChannelExec)channel).getErrStream()));
      channel.connect();
    }

    int exitStatus = -1;
    String line = null;
    String errorMsg = new String();

    try {
      while (true) {

        // Check for ready() to avoid thread blocking, then read
        // all available lines from the buffer and update progress
        while (stdOutput.ready()) {
          line = stdOutput.readLine();
          parseCaffeOutputString(line, plotImage, x, yTrain, yValid);
        }
        // Also read error stream to avoid stream overflow that leads
        // to process stalling
        while (stdError.ready()) {
          line = stdError.readLine();
          errorMsg += line + "\n";
          parseCaffeOutputString(line, plotImage, x, yTrain, yValid);
        }

        if (_sshSession != null) {
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
      _readyCancelButton.setText("Terminating...");
      _readyCancelButton.setEnabled(false);
      if (_sshSession != null) {
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
          IJ.log("Process could not be terminated using SIGTERM: " + eInner);
        }
        channel.disconnect();
      }
      else p.destroy();
      throw e;
    }
    if (_sshSession != null) channel.disconnect();

    if (exitStatus != 0) {
      IJ.log(errorMsg);
      throw new IOException(
          "Error during finetuning: exit status " + exitStatus +
          "\nSee log for further details");
    }

    if (_sshSession != null)
    {
      try {
        // Rename output file name and remove solverstate file
        UnetTools.renameFile(
            processFolder() + "/" + id() + "-snapshot_iter_" + nIter +
            ".caffemodel.h5", processFolder() + "/" +
            _outfileTextField.getText(), _sshSession, this);
      }
      catch (SftpException e) {
        IJ.showMessage(
            "Could not rename weightsfile to " + processFolder() + "/" +
            _outfileTextField.getText() + "\n" +
            "The trained model can be found at " + processFolder() + "/" +
            id() + "-snapshot_iter_" + nIter + ".caffemodel.h5");
      }
      try {
        UnetTools.removeFile(
            processFolder() + "/" + id() + "-snapshot_iter_" + nIter +
            ".solverstate.h5", _sshSession, this);
      }
      catch (SftpException e) {
        IJ.showMessage(
            "Could not delete solverstate " + processFolder() + "/" + id() +
            "-snapshot_iter_" + nIter + ".solverstate.h5");
      }
    }
    else {
      // Rename output file and remove solverstate
      File outfile = new File(
          processFolder() + "/" + _outfileTextField.getText());
      File infile = new File(
          processFolder() + "/" + id() + "-snapshot_iter_" + nIter +
          ".caffemodel.h5");
      File solverstatefile = new File(
          processFolder() + "/" + id() + "-snapshot_iter_" + nIter +
          ".solverstate.h5");
      if (!infile.renameTo(outfile))
          IJ.showMessage(
              "Could not rename weightsfile to " + outfile.getPath() + "\n" +
              "The trained model can be found at " + infile.getPath());
      if (!solverstatefile.delete())
          IJ.showMessage(
              "Could not delete solverstate " + solverstatefile.getPath());
    }
    setTaskProgress(1, 1);
  }

  @Override
  public void run(String arg) {
    boolean trainImageFound = false;
    if (WindowManager.getIDList() != null) {
      for (int id: WindowManager.getIDList()) {
        if (WindowManager.getImage(id).getOverlay() != null) {
          trainImageFound = true;
          break;
        }
      }
    }
    if (!trainImageFound) {
      IJ.error("U-Net Finetuning", "No image with annotations found for " +
               "finetuning.\nThis Plugin requires at least one image with " +
               "overlay containing annotations.");
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
    try
    {
      setProgress(0);
      if (_isInteractive && !getParameters()) return;

      model().modelPrototxtAbsolutePath =
          processFolder() + "/" + id() + "-model.prototxt";
      model().solverPrototxtAbsolutePath =
          processFolder() + "/" + id() + "-solver.prototxt";
      String trainFileListAbsolutePath =
          processFolder() + "/" + id() + "-trainfilelist.txt";
      String validFileListAbsolutePath =
          processFolder() + "/" + id() + "-validfilelist.txt";

      int nTrainImages = _trainFileList.getModel().getSize();
      int nValidImages = _validFileList.getModel().getSize();
      int nImages = nTrainImages + nValidImages;

      String[] trainBlobFileNames = new String[nTrainImages];
      Vector<String> validBlobFileNames = new Vector<String>();

      // Convert and upload caffe blobs
      try {
        File outfile = null;
        if (_sshSession != null)
        {
          outfile = File.createTempFile(id(), ".h5");
          outfile.delete();
        }

        // Process train files
        for (int i = 0; i < nTrainImages; i++) {
          setProgress((10 * i) / nImages);
          setTaskProgressRange(
              (int)(10 * i) / nImages,
              (int)(10 * (i + ((_sshSession != null) ? 0.5 : 1))) / nImages);
          trainBlobFileNames[i] =
              processFolder() + "/" + id() + "_train_" + i + ".h5";
          if (_sshSession == null) outfile = new File(trainBlobFileNames[i]);
          UnetTools.saveHDF5Blob(
              ((DefaultListModel<ImagePlus>)_trainFileList.getModel()).get(i),
              outfile, this, true);
          if (interrupted()) throw new InterruptedException();
          if (_sshSession != null) {
            setTaskProgressRange(
                (int)(10 * (i + 0.5)) / nImages, (int)(10 * (i + 1)) / nImages);
            _createdRemoteFolders.addAll(
                UnetTools.put(
                    outfile, trainBlobFileNames[i], _sshSession, this));
            _createdRemoteFiles.add(trainBlobFileNames[i]);
            if (interrupted()) throw new InterruptedException();
          }
        }

        // // Process validation files
        // for (int i = 0; i < nValidImages; i++) {
        //   setProgress((10 * (i + nTrainImages)) / nImages);
        //   setTaskProgressRange(
        //       (int)(10 * (i + nTrainImages)) / nImages,
        //       (int)(10 * (i + ((_sshSession != null) ? 0.5 : 1) +
        //                   nTrainImages)) / nImages);

        //   // Convert ImagePlus to data and annotation blobs
        //   ImagePlus dataBlob = UnetTools.convertToUnetFormat(
        //       ((DefaultListModel<ImagePlus>)_validFileList.getModel()).get(i),
        //       this, true);
        //   ImagePlus[] annotationBlobs =
        //       UnetTools.convertAnnotationsToLabelsAndWeights(imp, job);

        //   // Tiling
        //   int[] stride = model.getOutputTileShape(model().getTileShape());
        //   int[] tiling = new int[stride.length];
        //   int nTiles = 1;
        //   if (stride.length == 2) {
        //     tiling[0] = (int)(
        //         Math.ceil((double)dataBlob.getHeight() / (double)stride[0]));
        //     tiling[1] = (int)(
        //         Math.ceil((double)dataBlob.getWidth() / (double)stride[1]));
        //   }
        //   else {
        //     tiling[0] = (int)(
        //         Math.ceil((double)dataBlob.getNSlices() / (double)stride[0]));
        //     tiling[1] = (int)(
        //         Math.ceil((double)dataBlob.getHeight() / (double)stride[1]));
        //     tiling[2] = (int)(
        //         Math.ceil((double)dataBlob.getWidth() / (double)stride[2]));
        //   }
        //   for (int d = 0; d < stride.length; d++) nTiles *= tiling[d];

        //   if (_sshSession == null) outfile = new File(allBlobFileNames[i]);
        //   UnetTools.saveHDF5Blob(allImages[i], outfile, this, true);
        //   if (interrupted()) throw new InterruptedException();
        //   if (_sshSession != null) {
        //     setTaskProgressRange(
        //         (int)(10 * (i + 0.5)) / allImages.length,
        //         (int)(10 * (i + 1)) / allImages.length);
        //     _createdRemoteFolders.addAll(
        //         UnetTools.put(outfile, allBlobFileNames[i], _sshSession, this));
        //     _createdRemoteFiles.add(allBlobFileNames[i]);
        //     if (interrupted()) throw new InterruptedException();
        //   }
        // }
      }
      catch (IOException e) {
        IJ.error(e.toString());
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }
      catch (NotImplementedException e) {
        IJ.error("Could not create data blob: " + e);
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }
      catch (JSchException e) {
        IJ.error("Could not upload data blob: " + e);
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }
      catch (SftpException e) {
        IJ.error("Could not upload data blob: " + e);
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }

      // ---
      // Create train and valid file list files
      if (_sshSession != null) {
        try {
          File tmpFile = File.createTempFile(id(), "-trainfilelist.txt");
          BufferedWriter out = new BufferedWriter(new FileWriter(tmpFile));
          for (String fName : trainBlobFileNames) out.write(fName + "\n");
          out.close();
          _createdRemoteFolders.addAll(
              UnetTools.put(
                  tmpFile, trainFileListAbsolutePath, _sshSession, this));
          _createdRemoteFiles.add(trainFileListAbsolutePath);
          tmpFile.delete();
        }
        catch (IOException e) {
          IJ.error("Could not create temporary trainfile list: " + e);
          cleanUp();
          if (_jobTableModel != null) _jobTableModel.deleteJob(this);
          return;
        }
        catch (Exception e) {
          IJ.error("Could not upload trainfile list: " + e);
          cleanUp();
          if (_jobTableModel != null) _jobTableModel.deleteJob(this);
          return;
        }
      }
      else {
        try
        {
          File trainFileListFile = new File(trainFileListAbsolutePath);
          trainFileListFile.createNewFile();
          BufferedWriter out = new BufferedWriter(
              new FileWriter(trainFileListFile));
          for (String fName : trainBlobFileNames) out.write(fName + "\n");
          out.close();
          trainFileListFile.deleteOnExit();
        }
        catch (IOException e) {
          IJ.error("Could not create trainfile list: " + e);
          cleanUp();
          if (_jobTableModel != null) _jobTableModel.deleteJob(this);
          return;
        }
      }

      // if (validBlobFileNames.length != 0)
      // {
      //   if (_sshSession != null) {
      //     try {
      //       File tmpFile = File.createTempFile(id(), "-validfilelist.txt");
      //       BufferedWriter out = new BufferedWriter(new FileWriter(tmpFile));
      //       for (String fName : validBlobFileNames) out.write(fName + "\n");
      //       out.close();
      //       _createdRemoteFolders.addAll(
      //           UnetTools.put(
      //               tmpFile, validFileListAbsolutePath, _sshSession, this));
      //       _createdRemoteFiles.add(validFileListAbsolutePath);
      //       tmpFile.delete();
      //     }
      //     catch (IOException e) {
      //       IJ.error("Could not create temporary validfile list: " + e);
      //       cleanUp();
      //       if (_jobTableModel != null) _jobTableModel.deleteJob(this);
      //       return;
      //     }
      //     catch (Exception e) {
      //       IJ.error("Could not upload validfile list: " + e);
      //       cleanUp();
      //       if (_jobTableModel != null) _jobTableModel.deleteJob(this);
      //       return;
      //     }
      //   }
      //   else {
      //     try
      //     {
      //       File validFileListFile = new File(validFileListAbsolutePath);
      //       validFileListFile.createNewFile();
      //       BufferedWriter out = new BufferedWriter(
      //           new FileWriter(validFileListFile));
      //       for (String fName : validBlobFileNames) out.write(fName + "\n");
      //       out.close();
      //       validFileListFile.deleteOnExit();
      //     }
      //     catch (IOException e) {
      //       IJ.error("Could not create validfile list: " + e);
      //       cleanUp();
      //       if (_jobTableModel != null) _jobTableModel.deleteJob(this);
      //       return;
      //     }
      //   }
      // }

      // ---
      // Create prototxt files

      // model.prototxt
      try {
        Caffe.NetParameter.Builder nb = Caffe.NetParameter.newBuilder();
        TextFormat.getParser().merge(model().modelPrototxt, nb);

        boolean inputShapeSet = false;
        for (Caffe.LayerParameter.Builder lb : nb.getLayerBuilderList()) {
          if (lb.getType().equals("HDF5Data")) {
            lb.getHdf5DataParamBuilder().setSource(trainFileListAbsolutePath);
          }

          if (lb.getType().equals("CreateDeformation")) {
            if (model().elementSizeUm.length == 3) {
              lb.getCreateDeformationParamBuilder()
                  .setNz(model().getTileShape()[0])
                  .setNy(model().getTileShape()[1])
                  .setNx(model().getTileShape()[2]);
            }
            else {
              lb.getCreateDeformationParamBuilder()
                  .setNy(model().getTileShape()[0])
                  .setNx(model().getTileShape()[1]);
            }
            inputShapeSet = true;
          }
        }
        if (!inputShapeSet)
        {
          IJ.error(
              "The selected model cannot be finetuned using this Plugin.\n" +
              "It must contain a CreateDeformationLayer for data " +
              "augmentation.");
          return;
        }
        model().modelPrototxt = TextFormat.printToString(nb);
        if (_sshSession != null) {
          File tmpFile = File.createTempFile(id(), "-model.prototxt");
          model().saveModelPrototxt(tmpFile);
          _createdRemoteFolders.addAll(
              UnetTools.put(
                  tmpFile, model().modelPrototxtAbsolutePath,
                  _sshSession, this));
          _createdRemoteFiles.add(model().modelPrototxtAbsolutePath);
          tmpFile.delete();
        }
        else {
          File modelFile = new File(model().modelPrototxtAbsolutePath);
          modelFile.createNewFile();
          model().saveModelPrototxt(modelFile);
          modelFile.deleteOnExit();
        }
      }
      catch (IOException e) {
        IJ.error("Could not create temporary model.prototxt: " + e);
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }
      catch (Exception e) {
        IJ.error("Could not upload model.prototxt: " + e);
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }

      // solver.prototxt
      try {
        Caffe.SolverParameter.Builder sb = Caffe.SolverParameter.newBuilder();
        TextFormat.getParser().merge(model().solverPrototxt, sb);
        sb.setNet(model().modelPrototxtAbsolutePath);
        sb.setBaseLr(((Double)_learningRateTextField.getValue()).floatValue());
        sb.setSnapshot((Integer)_iterationsSpinner.getValue());
        sb.setMaxIter((Integer)_iterationsSpinner.getValue());
        sb.setSnapshotPrefix(processFolder() + "/" + id() + "-snapshot");
        sb.setLrPolicy("fixed");
        sb.setType("Adam");
        sb.setSnapshotFormat(Caffe.SolverParameter.SnapshotFormat.HDF5);
        model().solverPrototxt = TextFormat.printToString(sb);
        if (_sshSession != null) {
          File tmpFile = File.createTempFile(id(), "-solver.prototxt");
          model().saveSolverPrototxt(tmpFile);
          _createdRemoteFolders.addAll(
              UnetTools.put(
                  tmpFile, model().solverPrototxtAbsolutePath,
                  _sshSession, this));
          _createdRemoteFiles.add(model().solverPrototxtAbsolutePath);
          tmpFile.delete();
        }
        else {
          File solverFile = new File(model().solverPrototxtAbsolutePath);
          solverFile.createNewFile();
          model().saveSolverPrototxt(solverFile);
          solverFile.deleteOnExit();
        }
      }
      catch (IOException e) {
        IJ.error("Could not create solver: " + e);
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }
      catch (Exception e) {
        IJ.error("Could not upload solver.prototxt: " + e);
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }

      // Finetuning
      setTaskProgressRange(11, 100);
      try {
        runUnetFinetuning();
      }
      catch (JSchException e) {
        IJ.error("Could not upload data blob: " + e);
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
        return;
      }
      catch (IOException e) {
        IJ.error(e.toString());
        cleanUp();
        if (_jobTableModel != null) _jobTableModel.deleteJob(this);
      }
      if (interrupted()) throw new InterruptedException();

      setProgress(100);
      setReady(true);
    }
    catch (InterruptedException e) {
      IJ.showMessage("Job " + id() + " canceled. Cleaning up.");
      cleanUp();
      if (_jobTableModel != null) _jobTableModel.deleteJob(this);
      return;
    }
  }

};
