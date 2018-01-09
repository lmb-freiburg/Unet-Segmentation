import ij.Prefs;
import ij.IJ;
import ij.WindowManager;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.BorderLayout;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.JDialog;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JFileChooser;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.BorderFactory;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.io.File;
import java.io.FileFilter;
import java.util.Vector;
import java.util.UUID;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.JSchException;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

public class UnetJob extends Thread {

  private final String _jobId = "unet-" + UUID.randomUUID().toString();

  private final String[] gpuList = {
      "none", "all available", "GPU 0", "GPU 1", "GPU 2", "GPU 3",
      "GPU 4", "GPU 5", "GPU 6", "GPU 7" };

  protected TaskStatus _taskStatus = new TaskStatus();
  protected long _taskProgressMin = 0;
  protected long _taskProgressMax = 0;
  protected long _jobProgress = 0;

  protected boolean _isInteractive = true;
  protected boolean _finished = false;
  protected JButton _readyCancelButton = new JButton("Cancel");

  protected UnetJobTableModel _jobTableModel = null;

  protected JDialog _parametersDialog = null;
  protected GroupLayout _dialogLayout = null;
  protected Group _horizontalDialogLayoutGroup = null;
  protected Group _verticalDialogLayoutGroup = null;
  protected JPanel _configPanel = new JPanel();

  protected JComboBox<ModelDefinition> _modelComboBox =
      new JComboBox<ModelDefinition>();
  protected JTextField _weightsFileTextField = new JTextField("", 20);
  protected JTextField _processFolderTextField = new JTextField(
      Prefs.get("unet_segmentation.processfolder", ""), 20);
  protected JComboBox<String> _useGPUComboBox = new JComboBox<>(gpuList);

  protected HostConfigurationPanel _hostConfiguration = null;

  protected Session _sshSession;

  protected Vector<String> _createdRemoteFolders = new Vector<String>();
  protected Vector<String> _createdRemoteFiles = new Vector<String>();

  protected File _modelFolder = null;

  public String id() {
    return _jobId;
  }

  public void setJobTableModel(UnetJobTableModel jobTableModel) {
    _jobTableModel = jobTableModel;
  }

  public ModelDefinition model() {
    return (ModelDefinition)_modelComboBox.getSelectedItem();
  }

  public String weightsFileName() {
    return _weightsFileTextField.getText();
  }

  public String processFolder() {
    return _processFolderTextField.getText();
  }

  public String hostname() {
    if (_sshSession != null) return _sshSession.getHost();
    else return "localhost";
  }

  public String imageName() {
    return "all open images";
  }

  public void setTaskProgress(String status, long progress, long maxProgress) {
    _taskStatus.name = status;
    _taskStatus.progress = progress;
    _taskStatus.maxProgress = maxProgress;
    _taskStatus.isIndeterminate = (maxProgress == 0);
    if (_jobTableModel != null) {
      SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              _jobTableModel.updateJobStatus(_jobId);
            }});
    }
    else IJ.showStatus(_taskStatus.name);
    IJ.log(status);
  }

  public TaskStatus status() {
    return _taskStatus;
  }

  public void setTaskProgress(long progress, long maxProgress) {
    _taskStatus.progress = progress;
    _taskStatus.maxProgress = maxProgress;
    _taskStatus.isIndeterminate = (maxProgress == 0);
    if (_jobTableModel != null) {
      SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              _jobTableModel.updateJobStatus(_jobId);
            }});
    }
  }

  public void setTaskProgressRange(long min, long max) {
    _taskProgressMin = min;
    _taskProgressMax = max;
  }

  public void setTaskProgressMin(long min) {
    _taskProgressMin = min;
  }

  public long getTaskProgressMin() {
    return _taskProgressMin;
  }

  public void setTaskProgressMax(long max) {
    _taskProgressMax = max;
  }

  public long getTaskProgressMax() {
    return _taskProgressMax;
  }

  public long progress() {
    return _jobProgress;
  }

  public void setProgress(long progress, long progressMax) {
    _jobProgress = (100 * progress) / progressMax;
    if (_jobTableModel != null) {
      SwingUtilities.invokeLater(
          new Runnable() {
            @Override
            public void run() {
              _jobTableModel.updateJobProgress(_jobId);
            }});
    }
    else IJ.showProgress(_jobProgress / 100.0);
  }

  public void setProgress(long progress) {
    setProgress(progress, 100);
  }

  public JButton readyCancelButton() {
    return _readyCancelButton;
  }

  public boolean ready() {
    return _readyCancelButton.getText().equals("Ready");
  }

  protected void setReady(boolean ready) {
    _readyCancelButton.setText("Ready");
    if (_jobTableModel == null) return;
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            _jobTableModel.updateJobDownloadEnabled(_jobId);
          }});
  }

  public void finish() {
    if (_finished) return;
    cleanUp();
  }

  private void searchModels() {
    _modelComboBox.removeAllItems();
    if (_modelFolder != null && _modelFolder.isDirectory()) {
      File[] files = _modelFolder.listFiles(
          new FileFilter() {
            @Override public boolean accept(File file) {
              return file.getName().matches(".*[.]h5$");
            }});
      for (int i = 0; i < files.length; i++) {
        try {
          ModelDefinition model = new ModelDefinition(this);
          model.file = files[i];
          model.load();
          _modelComboBox.addItem(model);
        }
        catch (HDF5Exception e) {}
      }
      if (_modelComboBox.getItemCount() == 0)
          _modelComboBox.addItem(new ModelDefinition());
      else {
        String modelName = Prefs.get("unet_segmentation.modelName", "");
        for (int i = 0; i < _modelComboBox.getItemCount(); i++) {
          if (_modelComboBox.getItemAt(i).name.equals(modelName)) {
            _modelComboBox.setSelectedIndex(i);
            break;
          }
        }
        Prefs.set("unet_segmentation.modelDefinitionFolder",
                  _modelFolder.getAbsolutePath());
      }
    }
    _parametersDialog.invalidate();
    _parametersDialog.pack();
    _parametersDialog.setMinimumSize(_parametersDialog.getPreferredSize());
    _parametersDialog.setMaximumSize(
        new Dimension(_parametersDialog.getMaximumSize().width,
                      _parametersDialog.getPreferredSize().height));
    _parametersDialog.validate();
  }

  public String caffeGPUParameter() {
    String gpuParm = "";
    String selectedGPU = (String)_useGPUComboBox.getSelectedItem();
    if (selectedGPU.contains("GPU "))
        gpuParm = "-gpu " + selectedGPU.substring(selectedGPU.length() - 1);
    else if (selectedGPU.contains("all")) gpuParm = "-gpu all";
    return gpuParm;
  }

  protected void prepareParametersDialog() {
    /*******************************************************************
     * Generate the GUI layout without logic
     *******************************************************************/

    // Host configuration
    _hostConfiguration = new HostConfigurationPanel();

    // Model selection
    final JLabel modelLabel = new JLabel("Model:");
    _modelComboBox.setToolTipText("Select a caffe model.");
    final JButton modelFolderChooseButton;
    if (UIManager.get("FileView.directoryIcon") instanceof Icon)
        modelFolderChooseButton = new JButton(
            (Icon)UIManager.get("FileView.directoryIcon"));
    else modelFolderChooseButton = new JButton("...");
    int marginTop = (int) Math.ceil(
        (modelFolderChooseButton.getPreferredSize().getHeight() -
         _modelComboBox.getPreferredSize().getHeight()) / 2.0);
    int marginBottom = (int) Math.floor(
        (modelFolderChooseButton.getPreferredSize().getHeight() -
         _modelComboBox.getPreferredSize().getHeight()) / 2.0);
    Insets insets = modelFolderChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    modelFolderChooseButton.setMargin(insets);
    modelFolderChooseButton.setToolTipText(
        "Select local model definition folder");

    // Weights
    final JLabel weightsFileLabel = new JLabel("Weight file:");
    _weightsFileTextField.setToolTipText(
        "Location of the file containing the trained network weights " +
        "on the backend server.\nIf not yet on the server, on-the-fly " +
        "file upload will be offered.");
    final JButton weightsFileChooseButton =
        _hostConfiguration.weightsFileChooseButton();
    marginTop = (int) Math.ceil(
        (weightsFileChooseButton.getPreferredSize().getHeight() -
         _weightsFileTextField.getPreferredSize().getHeight()) / 2.0);
    marginBottom = (int) Math.floor(
        (weightsFileChooseButton.getPreferredSize().getHeight() -
         _weightsFileTextField.getPreferredSize().getHeight()) / 2.0);
    insets = weightsFileChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    weightsFileChooseButton.setMargin(insets);
    weightsFileChooseButton.setToolTipText(
        "Choose the file containing the trained network weights.");

    // Processing environment
    final JLabel processFolderLabel = new JLabel("Process Folder:");
    _processFolderTextField.setToolTipText(
        "Folder for temporary files on the backend server.");
    final JButton processFolderChooseButton =
        _hostConfiguration.processFolderChooseButton();
    marginTop = (int) Math.ceil(
        (processFolderChooseButton.getPreferredSize().getHeight() -
         _processFolderTextField.getPreferredSize().getHeight()) / 2.0);
    marginBottom = (int) Math.floor(
        (processFolderChooseButton.getPreferredSize().getHeight() -
         _processFolderTextField.getPreferredSize().getHeight()) / 2.0);
    insets = processFolderChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    processFolderChooseButton.setMargin(insets);
    processFolderChooseButton.setToolTipText(
        "Select the folder to store temporary files.");

    // GPU parameters
    final JLabel useGPULabel = new JLabel("Use GPU:");
    _useGPUComboBox.setToolTipText(
        "Select the GPU id to use. Select CPU if you don't have any " +
        "CUDA capable GPU available on the compute host. Select " +
        "<autodetect> to leave the choice to caffe.");

    final JPanel tilingModeSelectorPanel = new JPanel(new BorderLayout());
    final JPanel tilingParametersPanel = new JPanel(new BorderLayout());

    // Create Parameters Panel
    final JPanel dialogPanel = new JPanel();
    dialogPanel.setBorder(BorderFactory.createEtchedBorder());
    _dialogLayout = new GroupLayout(dialogPanel);
    dialogPanel.setLayout(_dialogLayout);
    _dialogLayout.setAutoCreateGaps(true);
    _dialogLayout.setAutoCreateContainerGaps(true);
    _horizontalDialogLayoutGroup =
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING);
    _verticalDialogLayoutGroup = _dialogLayout.createSequentialGroup();

    _dialogLayout.setHorizontalGroup(
        _horizontalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addGroup(
                _dialogLayout.createParallelGroup(
                    GroupLayout.Alignment.TRAILING)
                .addComponent(modelLabel)
                .addComponent(weightsFileLabel)
                .addComponent(processFolderLabel)
                .addComponent(useGPULabel)
                .addComponent(tilingModeSelectorPanel))
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_modelComboBox)
                    .addComponent(modelFolderChooseButton))
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_weightsFileTextField)
                    .addComponent(weightsFileChooseButton))
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_processFolderTextField)
                    .addComponent(processFolderChooseButton))
                .addComponent(_useGPUComboBox)
                .addComponent(tilingParametersPanel)))
        .addComponent(_hostConfiguration));

    _dialogLayout.setVerticalGroup(
        _verticalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(modelLabel)
            .addComponent(_modelComboBox)
            .addComponent(modelFolderChooseButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(weightsFileLabel)
            .addComponent(_weightsFileTextField)
            .addComponent(weightsFileChooseButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(processFolderLabel)
            .addComponent(_processFolderTextField)
            .addComponent(processFolderChooseButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(useGPULabel)
            .addComponent(_useGPUComboBox))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(
                tilingModeSelectorPanel, GroupLayout.PREFERRED_SIZE,
                GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(
                tilingParametersPanel, GroupLayout.PREFERRED_SIZE,
                GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
        .addComponent(_hostConfiguration));
    dialogPanel.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE, dialogPanel.getPreferredSize().height));

    // OK/Cancel buttons
    final JButton okButton = new JButton("OK");
    final JButton cancelButton = new JButton("Cancel");
    final JPanel okCancelPanel = new JPanel();
    okCancelPanel.add(okButton);
    okCancelPanel.add(cancelButton);

    // Assemble button panel
    final JPanel buttonPanel = new JPanel(new BorderLayout());
    buttonPanel.add(_configPanel, BorderLayout.WEST);
    buttonPanel.add(okCancelPanel, BorderLayout.EAST);

    // Assemble Dialog
    _parametersDialog = new JDialog(
        WindowManager.getCurrentWindow(), "U-net segmentation", true);
    _parametersDialog.add(dialogPanel, BorderLayout.CENTER);
    _parametersDialog.add(buttonPanel, BorderLayout.SOUTH);
    _parametersDialog.getRootPane().setDefaultButton(okButton);

    /*******************************************************************
     * Wire controls inner to outer before setting values so that
     * value changes trigger all required updates
     *******************************************************************/
    // Model selection affects weightFileTextField, tileModeSelector and
    // tilingParameters (critical)
    _modelComboBox.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              tilingModeSelectorPanel.removeAll();
              tilingParametersPanel.removeAll();
              if (model() != null) {
                _weightsFileTextField.setText(model().weightFile);
                tilingModeSelectorPanel.add(model().tileModeSelector);
                tilingModeSelectorPanel.setMinimumSize(
                    model().tileModeSelector.getPreferredSize());
                tilingModeSelectorPanel.setMaximumSize(
                    model().tileModeSelector.getPreferredSize());
                tilingParametersPanel.add(model().tileModePanel);
                tilingParametersPanel.setMinimumSize(
                    model().tileModePanel.getMinimumSize());
                tilingParametersPanel.setMaximumSize(
                    new Dimension(
                        Integer.MAX_VALUE,
                        model().tileModeSelector.getPreferredSize().height));
              }
              dialogPanel.setMaximumSize(
                  new Dimension(
                      Integer.MAX_VALUE,
                      dialogPanel.getPreferredSize().height));
              _parametersDialog.invalidate();
              _parametersDialog.setMinimumSize(
                  _parametersDialog.getPreferredSize());
              _parametersDialog.setMaximumSize(
                  new Dimension(
                      Integer.MAX_VALUE,
                      _parametersDialog.getPreferredSize().height));
              _parametersDialog.validate();
            }
          }});

    // Model folder selection affects ModelComboBox (critical)
    modelFolderChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFolder =
                (model() == null || model().file == null ||
                 model().file.getParentFile() == null) ?
                new File(".") : model().file.getParentFile();
            JFileChooser f = new JFileChooser(startFolder);
            f.setDialogTitle("Select U-net model folder");
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _modelFolder = f.getSelectedFile();
            // This also updates the Prefs if the folder contains valid models
            searchModels();
          }});

    // WeightsFileTextField affects model.weightFile (not critical)
    _weightsFileTextField.getDocument().addDocumentListener(
        new DocumentListener() {
          @Override
          public void insertUpdate(DocumentEvent e) {
            if (model() != null)
                model().weightFile = _weightsFileTextField.getText();
          }
          @Override
          public void removeUpdate(DocumentEvent e) {
            if (model() != null)
                model().weightFile = _weightsFileTextField.getText();
          }
          @Override
          public void changedUpdate(DocumentEvent e) {
            if (model() != null)
                model().weightFile = _weightsFileTextField.getText();
          }
        });

    // WeightsFileChooser affects WeightsFileTextField (not critical)
    weightsFileChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFile = new File(_weightsFileTextField.getText());
            JFileChooser f = new JFileChooser(startFile);
            f.setDialogTitle("Select trained U-net weights");
            f.setFileFilter(
                new FileNameExtensionFilter(
                    "HDF5 and prototxt files", "h5", "H5",
                    "prototxt", "PROTOTXT", "caffemodel",
                    "CAFFEMODEL"));
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _weightsFileTextField.setText(
                f.getSelectedFile().getAbsolutePath());
            model().weightFile = _weightsFileTextField.getText();
          }});

    // ProcessFolderChooser affects ProcessFolderTextField (not critical)
    processFolderChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFolder = new File(_processFolderTextField.getText());
            JFileChooser f = new JFileChooser(startFolder);
            f.setDialogTitle("Select (remote) processing folder");
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _processFolderTextField.setText(
                f.getSelectedFile().getAbsolutePath());
          }});

    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            // When accepted the dialog is only hidden. Don't
            // dispose it here, because isDisplayable() is used
            // to find out that OK was pressed!
            _parametersDialog.setVisible(false);
          }});

    cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            // When cancelled the dialog is disposed (which is
            // also done when the dialog is closed). It must be
            // disposed here, because isDisplayable() is used
            // to find out that the Dialog was cancelled!
            _parametersDialog.dispose();
            cleanUp();
          }});

    // Search models in currently selected model folder. This
    // populates the model combobox and sets _model if a model was
    // found. This should also implicitly update the tiling fields.
    _modelFolder = new File(
        Prefs.get("unet_segmentation.modelDefinitionFolder", "."));
    searchModels();

    // Set uncritical fields
    _useGPUComboBox.setSelectedItem(
        Prefs.get("unet_segmentation.gpuId", "none"));

    // Free all resources and make isDisplayable() return false to
    // distinguish dialog close from accept
    _parametersDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
  }

  public boolean checkParameters() {

    Prefs.set("unet_segmentation.gpuId",
              (String)_useGPUComboBox.getSelectedItem());

    if (!model().isValid()) {
      IJ.showMessage("Please select a model. Probably you first need " +
                     "to select a folder containing models.");
      return false;
    }

    Prefs.set("unet_segmentation.modelName", (String)model().name);
    model().savePreferences();

    if (_weightsFileTextField.getText() == "") {
      IJ.showMessage("Please enter the (remote) path to the weights file.");
      return false;
    }

    try {
      _sshSession = _hostConfiguration.sshSession();
    }
    catch (JSchException e) {
      IJ.log("SSH connection to '" + _hostConfiguration.hostname() +
             "' failed: " + e);
      IJ.showMessage(
          "Could not connect to remote host '" + _hostConfiguration.hostname() +
          "'\nPlease check your login credentials.\n" + e);
      return false;
    }

    return true;
  }

  public void cleanUp() {
    for (int i = 0; i < _createdRemoteFiles.size(); i++) {
      try {
        UnetTools.removeFile(
            _createdRemoteFiles.get(i), _sshSession, this);
      }
      catch (Exception e) {
        IJ.log("Could not remove temporary file " +
               _createdRemoteFiles.get(i) + ": " + e);
      }
    }
    for (int i = 0; i < _createdRemoteFolders.size(); i++) {
      try {
        UnetTools.removeFolder(
            _createdRemoteFolders.get(i), _sshSession, this);
      }
      catch (Exception e) {
        IJ.log("Could not remove temporary folder " +
               _createdRemoteFolders.get(i) + ": " + e);
      }
    }
    if (_sshSession != null) _sshSession.disconnect();
    _finished = true;
    if (_jobTableModel != null) _jobTableModel.deleteJob(this);
    IJ.log("U-net job finished");
  }

};
