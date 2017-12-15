import ij.Prefs;

import java.io.*;
import java.util.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

// HDF5 stuff
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.hdf5.HDF5FloatStorageFeatures;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ch.systemsx.cisd.hdf5.IHDF5WriterConfigurator;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;

public class ModelDefinition {

  private UnetJob _job;
  private int[] _minOutTileShape = null;

  public File file = null;
  public String remoteAbsolutePath = null;
  public String modelPrototxtAbsolutePath = null;
  public String solverPrototxtAbsolutePath = null;
  public String id = "Select model folder ==>";
  public String name = "Select model folder ==>";
  public String description = "Select model folder ==>";
  public String inputBlobName = null;
  public String inputDatasetName = "data";
  public String solverPrototxt = null;
  public String modelPrototxt = null;
  public String padding = null;
  public int normalizationType = 0;
  public int[] downsampleFactor = null;
  public int[] padInput = null;
  public int[] padOutput = null;
  public float[] elementSizeUm = null;
  public int[][] memoryMap = null;
  public float borderWeightFactor = 50.0f;
  public float borderWeightSigmaUm = 3.0f;
  public float foregroundBackgroundRatio = 0.1f;
  public float sigma1Um = 5.0f;

  public String weightFile = null;

  public JComboBox<String> tileModeSelector = new JComboBox<String>();
  public JPanel tileModePanel = new JPanel(new CardLayout());

  private static final String NTILES = "# Tiles:";
  private static final String GRID = "Grid (tiles):";
  private static final String SHAPE = "Tile shape (px):";
  private static final String MEMORY = "Memory (MB):";

  private JSpinner _nTilesSpinner = null;
  private JSpinner _shapeXSpinner = null;
  private JSpinner _shapeYSpinner = null;
  private JSpinner _shapeZSpinner = null;
  private JSpinner _gridXSpinner = null;
  private JSpinner _gridYSpinner = null;
  private JSpinner _gridZSpinner = null;
  private JSpinner _nPixelsPerTileSpinner = null;
  private JSpinner _gpuMemSpinner = null;

  public ModelDefinition() {
    _job = null;

    // Prepare empty GUI elements for this model
    tileModePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    tileModeSelector.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              ((CardLayout)tileModePanel.getLayout()).show(
                  tileModePanel, (String)tileModeSelector.getSelectedItem());
            }
          }});
  }

  public ModelDefinition(UnetJob job) {
    _job = job;

    // Prepare empty GUI elements for this model
    tileModePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    tileModeSelector.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              ((CardLayout)tileModePanel.getLayout()).show(
                  tileModePanel, (String)tileModeSelector.getSelectedItem());
            }
          }});
  }

  public boolean isValid() {
    return file != null;
  }

  private void createTileShapeCard() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    ((FlowLayout)panel.getLayout()).setAlignOnBaseline(true);

    panel.add(new JLabel(" x: "));
    int dsFactor = downsampleFactor[downsampleFactor.length - 1];
    int minValue = (_job != null && _job instanceof UnetSegmentationJob) ?
        _minOutTileShape[_minOutTileShape.length - 1] :
        ((padInput[padInput.length - 1] - padOutput[padOutput.length - 1]) +
         _minOutTileShape[_minOutTileShape.length - 1]);
    _shapeXSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".tileShapeX",
                           minValue + 10 * dsFactor),
            minValue, (int)Integer.MAX_VALUE, dsFactor));
    panel.add(_shapeXSpinner);

    dsFactor = downsampleFactor[downsampleFactor.length - 2];
    minValue = (_job != null && _job instanceof UnetSegmentationJob) ?
        _minOutTileShape[_minOutTileShape.length - 2] :
        ((padInput[padInput.length - 2] - padOutput[padOutput.length - 2]) +
         _minOutTileShape[_minOutTileShape.length - 2]);
    panel.add(new JLabel(" y: "));
    _shapeYSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".tileShapeY",
                           minValue + 10 * dsFactor),
            minValue, (int)Integer.MAX_VALUE, dsFactor));
    panel.add(_shapeYSpinner);

    if (elementSizeUm.length == 3) {
      dsFactor = downsampleFactor[downsampleFactor.length - 3];
      minValue = (_job != null && _job instanceof UnetSegmentationJob) ?
          _minOutTileShape[_minOutTileShape.length - 3] :
          ((padInput[padInput.length - 3] - padOutput[padOutput.length - 3]) +
           _minOutTileShape[_minOutTileShape.length - 3]);
      panel.add(new JLabel(" z: "));
      _shapeZSpinner = new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet_segmentation." + id + ".tileShapeZ",
                             minValue + 10 * dsFactor),
              minValue, (int)Integer.MAX_VALUE, dsFactor));
      panel.add(_shapeZSpinner);
    }
    tileModePanel.add(panel, SHAPE);
    tileModeSelector.addItem(SHAPE);
  }

  private void createNTilesCard() {
    _nTilesSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".nTiles", 1), 1,
            (int)Integer.MAX_VALUE, 1));
    tileModePanel.add(_nTilesSpinner, NTILES);
    tileModeSelector.addItem(NTILES);
  }

  private void createTileGridCard() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    ((FlowLayout)panel.getLayout()).setAlignOnBaseline(true);
    panel.add(new JLabel(" x: "));
    _gridXSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".tileGridX", 5), 1,
            (int)Integer.MAX_VALUE, 1));
    panel.add(_gridXSpinner);
    panel.add(new JLabel(" y: "));
    _gridYSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".tileGridY", 5), 1,
            (int)Integer.MAX_VALUE, 1));
    panel.add(_gridYSpinner);
    if (elementSizeUm.length == 3) {
      panel.add(new JLabel(" z: "));
      _gridZSpinner = new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet_segmentation." + id + ".tileGridZ", 5), 1,
              (int)Integer.MAX_VALUE, 1));
      panel.add(_gridZSpinner);
    }
    tileModePanel.add(panel, GRID);
    tileModeSelector.addItem(GRID);
  }

  private void createMemoryCard() {
    _gpuMemSpinner = new JSpinner(
        new SpinnerNumberModel(
            (int)Prefs.get("unet_segmentation." + id + ".GPUMemoryMB", 1000), 1,
            (int)Integer.MAX_VALUE, 1));
    tileModePanel.add(_gpuMemSpinner, MEMORY);
    tileModeSelector.addItem(MEMORY);
  }

  public void load(File inputFile) throws HDF5Exception {
    IHDF5Reader reader = HDF5Factory.configureForReading(inputFile).reader();
    file = inputFile;
    id = reader.string().read("/.unet-ident");
    name = reader.string().read("/unet_param/name");
    description = reader.string().read("/unet_param/description");
    inputBlobName = reader.string().read("/unet_param/input_blob_name");
    try {
      inputDatasetName = reader.string().read("/unet_param/input_dataset_name");
    }
    catch (HDF5Exception e) {}
    solverPrototxt = reader.string().read("/solver_prototxt");
    modelPrototxt = reader.string().read("/model_prototxt");
    padding = reader.string().read("/unet_param/padding");
    normalizationType = reader.int32().read("/unet_param/normalization_type");
    downsampleFactor = reader.int32().readArray("/unet_param/downsampleFactor");
    padInput = reader.int32().readArray("/unet_param/padInput");
    padOutput = reader.int32().readArray("/unet_param/padOutput");
    elementSizeUm = reader.float32().readArray("/unet_param/element_size_um");
    try {
      borderWeightFactor = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/borderWeightFactor");
      borderWeightSigmaUm = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/borderWeightSigmaUm");
      foregroundBackgroundRatio = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/foregroundBackgroundRatio");
      sigma1Um = reader.float32().read(
          "/unet_param/pixelwise_loss_weights/sigma1_um");
    }
    catch (HDF5Exception e) {}

    // Convert scalar parameters to vectors
    if (downsampleFactor.length == 1) {
      int dsFactor = downsampleFactor[0];
      downsampleFactor = new int[elementSizeUm.length];
      for (int d = 0; d < elementSizeUm.length; d++)
          downsampleFactor[d] = dsFactor;
    }
    if (padInput.length == 1) {
      int pad = padInput[0];
      padInput = new int[elementSizeUm.length];
      for (int d = 0; d < elementSizeUm.length; d++)
          padInput[d] = pad;
    }
    if (padOutput.length == 1) {
      int pad = padOutput[0];
      padOutput = new int[elementSizeUm.length];
      for (int d = 0; d < elementSizeUm.length; d++)
          padOutput[d] = pad;
    }

    // Compute minimum output tile shape
    _minOutTileShape = new int[elementSizeUm.length];
    for (int d = 0; d < padInput.length; d++) {
      _minOutTileShape[d] = padOutput[d];
      while (_minOutTileShape[d] < 0)
          _minOutTileShape[d] += downsampleFactor[d];
    }

    try {
      memoryMap = reader.int32().readMatrix(
          "/unet_param/mapInputNumPxGPUMemMB");
    }
    catch (HDF5Exception e) {
      memoryMap = null;
    }
    reader.close();

    // Clear and then recreate the GUI elements for this model
    tileModeSelector.removeAllItems();
    tileModePanel.removeAll();
    createTileShapeCard();
    if (_job != null && _job instanceof UnetSegmentationJob) {
      if (elementSizeUm.length == 2) {
        createNTilesCard();
        createTileGridCard();
      }
      if (memoryMap != null) createMemoryCard();
      tileModeSelector.setSelectedItem(
          (String)Prefs.get("unet_segmentation." + id + ".tilingOption",
                            (String)tileModeSelector.getSelectedItem()));
    }
    weightFile = Prefs.get("unet_segmentation." + id + ".weightFile", "");
  }

  public void load() throws HDF5Exception {
    load(file);
  }

  public void saveModelPrototxt(File f) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(f));
    writer.write(modelPrototxt);
    writer.flush();
  }

  public void saveSolverPrototxt(File f) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(f));
    writer.write(solverPrototxt);
    writer.flush();
  }

  public void save(File outputFile) throws HDF5Exception {
    IHDF5Writer writer =
        HDF5Factory.configure(outputFile).
        syncMode(IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
        .useSimpleDataSpaceForAttributes()
        .useUTF8CharacterEncoding().writer();
    file = outputFile;
    writer.string().write("/.unet-ident", id);
    writer.string().write("/unet_param/name", name);
    writer.string().write("/unet_param/description", description);
    writer.string().write("/unet_param/input_blob_name", inputBlobName);
    writer.string().write("/unet_param/input_dataset_name", inputDatasetName);
    writer.string().write("/solver_prototxt", solverPrototxt);
    writer.string().write("/model_prototxt", modelPrototxt);
    writer.string().write("/unet_param/padding", padding);
    writer.int32().write("/unet_param/normalization_type", normalizationType);
    writer.int32().writeArray("/unet_param/downsampleFactor", downsampleFactor);
    writer.int32().writeArray("/unet_param/padInput", padInput);
    writer.int32().writeArray("/unet_param/padOutput", padOutput);
    writer.float32().writeArray("/unet_param/element_size_um", elementSizeUm);
    writer.float32().write(
        "/unet_param/pixelwise_loss_weights/borderWeightFactor",
        borderWeightFactor);
    writer.float32().write(
          "/unet_param/pixelwise_loss_weights/borderWeightSigmaUm",
          borderWeightSigmaUm);
    writer.float32().write(
        "/unet_param/pixelwise_loss_weights/foregroundBackgroundRatio",
        foregroundBackgroundRatio);
    writer.float32().write(
        "/unet_param/pixelwise_loss_weights/sigma1_um", sigma1Um);
    if (memoryMap != null && memoryMap.length == 2)
        writer.int32().writeMatrix(
            "/unet_param/mapInputNumPxGPUMemMB", memoryMap);
    writer.close();
  }

  public void save() throws HDF5Exception {
    save(file);
  }

  public void setFromTilingParameterString(String tilingParameter) {
    String[] arg = tilingParameter.split("=");
    if (arg[0].equals(NTILES)) {
      tileModeSelector.setSelectedItem(NTILES);
      _nTilesSpinner.setValue(Integer.valueOf(arg[1]));
      return;
    }
    if (arg[0].equals(GRID)) {
      tileModeSelector.setSelectedItem(GRID);
      String[] st = arg[1].split("x");
      if (st.length > 0) _gridXSpinner.setValue(Integer.valueOf(st[0]));
      else _gridXSpinner.setValue(1);
      if (st.length > 1) _gridYSpinner.setValue(Integer.valueOf(st[1]));
      else _gridYSpinner.setValue(1);
      if (elementSizeUm.length == 3) {
        if (st.length > 2) _gridZSpinner.setValue(Integer.valueOf(st[2]));
        else _gridZSpinner.setValue(1);
      }
      return;
    }
    if (arg[0].equals(SHAPE)) {
      tileModeSelector.setSelectedItem(SHAPE);
      String[] st = arg[1].split("x");
      if (st.length > 0) _shapeXSpinner.setValue(Integer.valueOf(st[0]));
      else _shapeXSpinner.setValue(
          (Integer)(
              (SpinnerNumberModel)_shapeXSpinner.getModel()).getMinimum());
      if (st.length > 1) _shapeYSpinner.setValue(Integer.valueOf(st[1]));
      else _shapeYSpinner.setValue(
          (Integer)(
              (SpinnerNumberModel)_shapeYSpinner.getModel()).getMinimum());
      if (elementSizeUm.length == 3) {
        if (st.length > 2)
            _shapeZSpinner.setValue(Integer.valueOf(st[2]));
        else _shapeZSpinner.setValue(
            (Integer)(
                (SpinnerNumberModel)_shapeXSpinner.getModel()).getMinimum());
      }
      return;
    }
    if (arg[0].equals(MEMORY)) {
      tileModeSelector.setSelectedItem(MEMORY);
      _gpuMemSpinner.setValue(Integer.valueOf(arg[1]));
      return;
    }
  }

  public String getTilingParameterString() {
    if (((String)tileModeSelector.getSelectedItem()).equals(NTILES))
        return NTILES + "=" + (Integer)_nTilesSpinner.getValue();
    if (((String)tileModeSelector.getSelectedItem()).equals(GRID))
        return GRID + "=" + (Integer)_gridXSpinner.getValue() + "x" +
            (Integer)_gridYSpinner.getValue() +
            ((elementSizeUm.length == 3) ?
             ("x" + (Integer)_gridZSpinner.getValue()) : "");
    if (((String)tileModeSelector.getSelectedItem()).equals(SHAPE))
        return SHAPE + "=" + (Integer)_shapeXSpinner.getValue() + "x" +
            (Integer)_shapeYSpinner.getValue() +
            ((elementSizeUm.length == 3) ?
             ("x" + (Integer)_shapeZSpinner.getValue()) : "");
    if (((String)tileModeSelector.getSelectedItem()).equals(MEMORY))
        return MEMORY + "=" + (Integer)_gpuMemSpinner.getValue();
    throw new UnsupportedOperationException(
        "Cannot handle unknown tiling mode '" +
        (String)tileModeSelector.getSelectedItem() + "'");
  }

  public String getCaffeTilingParameter() throws UnsupportedOperationException {
    if (((String)tileModeSelector.getSelectedItem()).equals(NTILES))
        return "-n_tiles " + (Integer)_nTilesSpinner.getValue();
    if (((String)tileModeSelector.getSelectedItem()).equals(GRID))
        return "-n_tiles " +
            ((elementSizeUm.length == 3) ?
             ((Integer)_gridZSpinner.getValue() + "x") : "") +
            (Integer)_gridYSpinner.getValue() + "x" +
            (Integer)_gridXSpinner.getValue();
    if (((String)tileModeSelector.getSelectedItem()).equals(SHAPE))
        return "-tile_size " +
            ((elementSizeUm.length == 3) ?
             ((Integer)_shapeZSpinner.getValue() + "x") : "") +
            (Integer)_shapeYSpinner.getValue() + "x" +
            (Integer)_shapeXSpinner.getValue();
    if (((String)tileModeSelector.getSelectedItem()).equals(MEMORY))
        return "-gpu_mem_available_MB " +
            (Integer)_gpuMemSpinner.getValue();
    throw new UnsupportedOperationException(
        "Cannot handle unknown tiling mode '" +
        (String)tileModeSelector.getSelectedItem() + "'");
  }

  public String getProtobufTileShapeString() {
    return ((elementSizeUm.length == 3) ?
            ("nz: " + (Integer)_shapeZSpinner.getValue()) + " " : "") +
        "ny: " + (Integer)_shapeYSpinner.getValue() +
        " nx: " + (Integer)_shapeXSpinner.getValue();
  }

  public int[] getTileShape() {
    int[] res = new int[elementSizeUm.length];
    if (elementSizeUm.length == 3) {
      res[0] = (Integer)_shapeZSpinner.getValue();
      res[1] = (Integer)_shapeYSpinner.getValue();
      res[2] = (Integer)_shapeXSpinner.getValue();
    }
    else {
      res[0] = (Integer)_shapeYSpinner.getValue();
      res[1] = (Integer)_shapeXSpinner.getValue();
    }
    return res;
  }

  public void savePreferences() {
    Prefs.set("unet_segmentation." + id + ".weightFile", weightFile);
    if (_job != null && _job instanceof UnetSegmentationJob)
        Prefs.set("unet_segmentation." + id + ".tilingOption",
                  (String)tileModeSelector.getSelectedItem());

    if (((String)tileModeSelector.getSelectedItem()).equals(NTILES)) {
      Prefs.set("unet_segmentation." + id + ".nTiles",
                (Integer)_nTilesSpinner.getValue());
      return;
    }

    if (((String)tileModeSelector.getSelectedItem()).equals(GRID)) {
      Prefs.set("unet_segmentation." + id + ".tileGridX",
                (Integer)_gridXSpinner.getValue());
      Prefs.set("unet_segmentation." + id + ".tileGridY",
                (Integer)_gridYSpinner.getValue());
      if (elementSizeUm.length == 3)
          Prefs.set("unet_segmentation." + id + ".tileGridZ",
                    (Integer)_gridZSpinner.getValue());
      return;
    }

    if (((String)tileModeSelector.getSelectedItem()).equals(SHAPE)) {
      Prefs.set("unet_segmentation." + id + ".tileShapeX",
                (Integer)_shapeXSpinner.getValue());
      Prefs.set("unet_segmentation." + id + ".tileShapeY",
                (Integer)_shapeYSpinner.getValue());
      if (elementSizeUm.length == 3)
          Prefs.set("unet_segmentation." + id + ".tileShapeZ",
                    (Integer)_shapeZSpinner.getValue());
      return;
    }

    if (((String)tileModeSelector.getSelectedItem()).equals(MEMORY)) {
      Prefs.set("unet_segmentation." + id + ".GPUMemoryMB",
                (Integer)_gpuMemSpinner.getValue());
      return;
    }

    throw new UnsupportedOperationException(
        "Cannot handle unknown tiling mode '" +
        (String)tileModeSelector.getSelectedItem() + "'");
  }

  @Override
  public String toString() {
    return name;
  }

};
