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

import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import ij.Prefs;
import ij.gui.Roi;
import ij.gui.ImageRoi;
import ij.WindowManager;
import ij.plugin.frame.Recorder;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JCheckBox;
import javax.swing.GroupLayout;
import javax.swing.UIManager;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

import java.io.File;
import java.io.IOException;

import java.util.Vector;
import java.util.Map;
import java.util.HashMap;

public class CreateBlobsJob extends Job implements PlugIn {

  private final JPanel _elSizePanel = new JPanel(new BorderLayout());
  protected final JButton _fromImageButton = new JButton("from Image");

  private ImagePlus _imp = null;
  private final JCheckBox _labelsAreClassesCheckBox =
      new JCheckBox("Labels are classes",
                    Prefs.get("unet.finetuning.labelsAreClasses", true));
  private final JTextField _outFileTextField = new JTextField(
      Prefs.get("unet.createblobs.filename", ""));
  private final JButton _outFileChooseButton =
      (UIManager.get("FileView.directoryIcon") instanceof Icon) ?
      new JButton((Icon)UIManager.get("FileView.directoryIcon")) :
      new JButton("...");
  private final JTextField _classesTextField = new JTextField(
      Prefs.get("unet.createblobs.classes", "Foreground"));
  private final JCheckBox _showBlobsCheckBox =
      new JCheckBox(
          "Show Blobs", Prefs.get("unet.createblobs.showblobs", false));

  public CreateBlobsJob() {
    super();
  }

  public CreateBlobsJob(JobTableModel model) {
    super(model);
  }

  public void setImagePlus(ImagePlus imp) {
    this._imp = imp;
  }

  @Override
  protected void processModelSelectionChange() {
    _elSizePanel.removeAll();
    if (model() != null) {
      _elSizePanel.add(model().elementSizeUmPanel());
      _elSizePanel.setMinimumSize(
          model().elementSizeUmPanel().getMinimumSize());
      _elSizePanel.setMaximumSize(
          new Dimension(
              Integer.MAX_VALUE,
              model().elementSizeUmPanel().getPreferredSize().height));
    }
    super.processModelSelectionChange();
  }

  @Override
  protected void createDialogElements() {

    super.createDialogElements();

    _parametersDialog.setTitle("U-Net Blob Creation");

    JLabel elSizeLabel = new JLabel("Element Size [µm]:");
    _fromImageButton.setToolTipText(
        "Use native image element size for finetuning");

    JLabel outFileLabel = new JLabel("Output HDF5 File:");
    int marginTop = (int) Math.ceil(
        (_outFileChooseButton.getPreferredSize().getHeight() -
         _outFileTextField.getPreferredSize().getHeight()) / 2.0);
    int marginBottom = (int) Math.floor(
        (_outFileChooseButton.getPreferredSize().getHeight() -
         _outFileTextField.getPreferredSize().getHeight()) / 2.0);
    Insets insets = _outFileChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    _outFileChooseButton.setMargin(insets);
    _outFileTextField.setToolTipText("Enter output HDF5 (.h5) file name");
    _outFileChooseButton.setToolTipText("Select output HDF5 (.h5) file name");

    JLabel classesLabel = new JLabel("Classes:");
    _classesTextField.setToolTipText(
        "Semicolon-separated list of classes (ROI labels) occuring in the " +
        "training set. Don't include background as class!");

    _horizontalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addGroup(
                _dialogLayout.createParallelGroup(
                    GroupLayout.Alignment.TRAILING)
                .addComponent(elSizeLabel).addComponent(outFileLabel)
                .addComponent(classesLabel))
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_elSizePanel).addComponent(_fromImageButton))
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_outFileTextField)
                    .addComponent(_outFileChooseButton))
                .addComponent(_classesTextField)));

    _verticalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(elSizeLabel).addComponent(_elSizePanel)
            .addComponent(_fromImageButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(outFileLabel).addComponent(_outFileTextField)
            .addComponent(_outFileChooseButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(classesLabel).addComponent(_classesTextField));

    _labelsAreClassesCheckBox.setToolTipText(
        "Check if your labels indicate class labels");
    _configPanel.add(_labelsAreClassesCheckBox);
    _classesTextField.setEnabled(_labelsAreClassesCheckBox.isSelected());
    _showBlobsCheckBox.setToolTipText(
        "Check if you want to see the blobs as ImagePlus windows");
    _configPanel.add(_showBlobsCheckBox);
  }

  @Override
  protected void finalizeDialog() {
    _fromImageButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (model() == null) return;
            model().setElementSizeUm(Tools.getElementSizeUm(_imp));
          }});
    _labelsAreClassesCheckBox.addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            _classesTextField.setEnabled(
                _labelsAreClassesCheckBox.isSelected());
          }});
    _outFileChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFolder = new File(_outFileTextField.getText());
            JFileChooser f = new JFileChooser(startFolder);
            f.setDialogTitle("Select output file name");
            f.setFileFilter(
                new FileNameExtensionFilter("HDF5 files", "h5", "H5"));
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _outFileTextField.setText(f.getSelectedFile().getAbsolutePath());
          }});
    super.finalizeDialog();
  }

  private boolean getParameters() throws InterruptedException {

    _imp = IJ.getImage();

    if (_imp == null) {
      IJ.noImage();
      return false;
    }

    if (_imp.getOverlay() == null) {
      showMessage(
          "The selected image does not contain an overlay with annotations.");
      return false;
    }

    do {
      progressMonitor().count("Waiting for user input", 0);
      _parametersDialog.setVisible(true);

      // Dialog was cancelled
      if (!_parametersDialog.isDisplayable())
          throw new InterruptedException("Dialog canceled");
    }
    while (!checkParameters());

    _parametersDialog.dispose();

    if (jobTable() != null) jobTable().fireTableDataChanged();

    return true;
  }

  @Override
  protected boolean checkParameters() throws InterruptedException {
    if (!super.checkParameters()) return false;

    if (_outFileTextField.getText().isEmpty() &&
        !_showBlobsCheckBox.isSelected()) _showBlobsCheckBox.setSelected(true);

    String[] classes = _classesTextField.getText().split(";");
    if (_labelsAreClassesCheckBox.isSelected() && classes.length == 0) {
      showMessage("Please provide a semicolon-separated list of class " +
                  "names.\nThe list must contain the class substring of " +
                  "all ROI labels.\n" +
                  "Integer labels from mask segmentations are assigned " +
                  "class names in the given order.\n" +
                  "Background (0) is already contained, therefore, " +
                  "do not explicitly add the background class to the list of " +
                  "classes");
      return false;
    }

    Prefs.set("unet.createblobs.filename", _outFileTextField.getText());
    Prefs.set("unet.createblobs.classes", _classesTextField.getText());
    Prefs.set("unet.createblobs.showblobs", _showBlobsCheckBox.isSelected());

    return true;
  }

  @Override
  public void run(String arg) {
    JobManager.instance().addJob(this);
    start();
  }

  public static void createBlobs(String params) {
    final CreateBlobsJob job = new CreateBlobsJob();
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
    model.setElementSizeUm(parameters.get("elementSizeUm"));
    job._outFileTextField.setText(parameters.get("outputFileName"));
    job._labelsAreClassesCheckBox.setSelected(
        parameters.get("labelsAreClasses").equals("1"));
    if (job._labelsAreClassesCheckBox.isSelected())
        job._classesTextField.setText(parameters.get("classes"));
    job._showBlobsCheckBox.setSelected(
        parameters.get("showBlobs").equals("1"));
    job.setInteractive(false);

    // Run blocking on current thread
    job.run();
  }

  @Override
  public void run() {
    try
    {
      prepareParametersDialog();
      if (isInteractive() && !getParameters()) return;

      boolean labelsAreClasses = _labelsAreClassesCheckBox.isSelected();
      if (labelsAreClasses) {
        String[] classes = _classesTextField.getText().split(";");
        model().classNames = new String[classes.length + 1];
        model().classNames[0] = "Background";
        for (int i = 0; i < classes.length; ++i)
            model().classNames[i+1] = classes[i];
      }
      else model().classNames = new String[] { "Background", "Foreground" };

      TrainingSample t = new TrainingSample(_imp);
      progressMonitor().push("Converting data", 0.1f, 0.2f);
      t.createDataBlob(model(), progressMonitor());
      if (interrupted()) throw new InterruptedException();
      progressMonitor().pop();
      progressMonitor().push("Converting labels", 0.2f, 1.0f);
      t.createLabelsAndWeightsBlobs(
          model(), labelsAreClasses, progressMonitor());

      if (!_outFileTextField.getText().isEmpty())
          t.saveBlobs(
              new File(
                  _outFileTextField.getText()), model(), progressMonitor());

      if (_showBlobsCheckBox.isSelected()) {
        t.dataBlob().show();
        t.dataBlob().updateAndDraw();
        t.labelBlob().show();
        t.labelBlob().updateAndDraw();
        t.weightBlob().show();
        t.weightBlob().updateAndDraw();
        t.samplePdfBlob().show();
        t.samplePdfBlob().updateAndDraw();
      }

      if (interrupted()) throw new InterruptedException();
      progressMonitor().end();
      setReady(true);

      if (Recorder.record) {
        Recorder.setCommand(null);
        // ImageJ macros treat backslash characters as escape
        // characters, therefore, replace all backslash characters
        // by slash characters to make the call platform-independent
        String command =
            "call('de.unifreiburg.unet.CreateBlobsJob." +
            "createBlobs', " +
            "'modelFilename=" +
            model().file.getAbsolutePath().replace("\\", "/") +
            ",elementSizeUm=" + model().getElementSizeUmString() +
            ",outputFileName=" + _outFileTextField.getText() +
            ",labelsAreClasses=" +
            (_labelsAreClassesCheckBox.isSelected() ? "1" : "0") +
            ",classes=" + _classesTextField.getText() +
            ",showBlobs=" + (_showBlobsCheckBox.isSelected() ? "1" : "0") +
            "');\n";
        Recorder.recordString(command);
      }
    }
    catch (TrainingSampleException e) {
      showError("Invalid Training Sample", e);
      abort();
    }
    catch (BlobException e) {
      showError("Blob conversion failed\n" + e.getMessage(), e);
      abort();
    }
    catch (IOException e) {
      showError("Could not create output file. Insufficient permissions?", e);
      abort();
    }
    catch (InterruptedException e) {
      abort();
    }
  }

};
