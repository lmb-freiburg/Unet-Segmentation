import ij.*;

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

public class ModelDefinition
{

  public File file = null;
  public String remoteAbsolutePath = null;
  public String id = "Select model folder ==>";
  public String name = "Select model folder ==>";
  public String description = "Select model folder ==>";
  public String inputBlobName = null;
  public String prototxt = null;
  public String padding = null;
  public int nChannels = 0;
  public int normalizationType = 0;
  public int[] downsampleFactor = null;
  public int[] padInput = null;
  public int[] padOutput = null;
  public float[] elementSizeUm = null;
  public int[][] memoryMap = null;

  public String weightFile = null;

  public JComboBox<String> tileModeSelector = new JComboBox<String>();
  public JPanel tileModePanel = new JPanel(new CardLayout());

  private static final String NTILES = "# Tiles:";
  private static final String GRID = "Grid (tiles):";
  private static final String SHAPE = "Tile shape (px):";
  private static final String NPIXELSPERTILE = "# Pixels/Tile:";
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

  public ModelDefinition()
        {
          // Prepare empty GUI elements for this model
          tileModePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
          tileModeSelector.addItemListener(new ItemListener()
              {
                @Override
                public void itemStateChanged(ItemEvent e)
                      {
                        if (e.getStateChange() == ItemEvent.SELECTED)
                        {
                          ((CardLayout)tileModePanel.getLayout()).show(
                              tileModePanel,
                              (String)tileModeSelector.getSelectedItem());
                        }
                      }
              });
        }

  public boolean isValid()
        {
          return file != null;
        }

  private void createTileShapeCard()
        {
          JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
          panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
          ((FlowLayout)panel.getLayout()).setAlignOnBaseline(true);
          panel.add(new JLabel(" x: "));
          _shapeXSpinner = new JSpinner(
              new SpinnerNumberModel(
                  (int)Prefs.get(
                      "unet_segmentation." + id + ".tileShapeX", 100), 1,
                  (int)Integer.MAX_VALUE, 1));
          panel.add(_shapeXSpinner);
          panel.add(new JLabel(" y: "));
          _shapeYSpinner = new JSpinner(
              new SpinnerNumberModel(
                  (int)Prefs.get(
                      "unet_segmentation." + id + ".tileShapeY", 100), 1,
                  (int)Integer.MAX_VALUE, 1));
          panel.add(_shapeYSpinner);
          if (elementSizeUm.length == 3)
          {
            panel.add(new JLabel(" z: "));
            _shapeZSpinner = new JSpinner(
                new SpinnerNumberModel(
                    (int)Prefs.get(
                        "unet_segmentation." + id + ".tileShapeZ", 100), 1,
                    (int)Integer.MAX_VALUE, 1));
            panel.add(_shapeZSpinner);
          }
          tileModePanel.add(panel, SHAPE);
          tileModeSelector.addItem(SHAPE);
        }

  private void createNTilesCard()
        {
          _nTilesSpinner = new JSpinner(
              new SpinnerNumberModel(
                  (int)Prefs.get("unet_segmentation." + id + ".nTiles", 1), 1,
                  (int)Integer.MAX_VALUE, 1));
          tileModePanel.add(_nTilesSpinner, NTILES);
          tileModeSelector.addItem(NTILES);
        }

  private void createTileGridCard()
        {
          JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
          panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
          ((FlowLayout)panel.getLayout()).setAlignOnBaseline(true);
          panel.add(new JLabel(" x: "));
          _gridXSpinner = new JSpinner(
              new SpinnerNumberModel(
                  (int)Prefs.get(
                      "unet_segmentation." + id + ".tileGridX", 5), 1,
                  (int)Integer.MAX_VALUE, 1));
          panel.add(_gridXSpinner);
          panel.add(new JLabel(" y: "));
          _gridYSpinner = new JSpinner(
              new SpinnerNumberModel(
                  (int)Prefs.get(
                      "unet_segmentation." + id + ".tileGridY", 5), 1,
                  (int)Integer.MAX_VALUE, 1));
          panel.add(_gridYSpinner);
          if (elementSizeUm.length == 3)
          {
            panel.add(new JLabel(" z: "));
            _gridZSpinner = new JSpinner(
                new SpinnerNumberModel(
                    (int)Prefs.get(
                        "unet_segmentation." + id + ".tileGridZ", 5), 1,
                    (int)Integer.MAX_VALUE, 1));
            panel.add(_gridZSpinner);
          }
          tileModePanel.add(panel, GRID);
          tileModeSelector.addItem(GRID);
        }

  private void createNPixelsPerTileCard()
        {
          _nPixelsPerTileSpinner = new JSpinner(
              new SpinnerNumberModel(
                  (int)Prefs.get(
                      "unet_segmentation." + id + ".nPixelsPerTile", 300000), 1,
                  (int)Integer.MAX_VALUE, 1));
          tileModePanel.add(_nPixelsPerTileSpinner, NPIXELSPERTILE);
          tileModeSelector.addItem(NPIXELSPERTILE);
        }

  private void createMemoryCard()
        {
          _gpuMemSpinner = new JSpinner(
              new SpinnerNumberModel(
                  (int)Prefs.get(
                      "unet_segmentation." + id + ".GPUMemoryMB", 1000), 1,
                  (int)Integer.MAX_VALUE, 1));
          tileModePanel.add(_gpuMemSpinner, MEMORY);
          tileModeSelector.addItem(MEMORY);
        }

  public void load(File inputFile) throws HDF5Exception
        {
          IHDF5Reader reader =
              HDF5Factory.configureForReading(inputFile).reader();
          file = inputFile;
          id = reader.string().read("/.unet-ident");
          name = reader.string().read("/unet_param/name");
          description = reader.string().read("/unet_param/description");
          inputBlobName = reader.string().read("/unet_param/input_blob_name");
          prototxt = reader.string().read("/model_prototxt");
          padding = reader.string().read("/unet_param/padding");
          nChannels = reader.int32().read("/unet_param/input_num_channels");
          normalizationType = reader.int32().read(
              "/unet_param/normalization_type");
          downsampleFactor = reader.int32().readArray(
              "/unet_param/downsampleFactor");
          padInput = reader.int32().readArray("/unet_param/padInput");
          padOutput = reader.int32().readArray("/unet_param/padOutput");
          elementSizeUm = reader.float32().readArray(
              "/unet_param/element_size_um");
          try
          {
            memoryMap = reader.int32().readMatrix(
                "/unet_param/mapInputNumPxGPUMemMB");
          }
          catch (HDF5Exception e)
          {
            memoryMap = null;
          }
          reader.close();

          // Clear and then recreate the GUI elements for this model
          tileModeSelector.removeAllItems();
          tileModePanel.removeAll();
          createTileShapeCard();
          if (elementSizeUm.length == 2)
          {
            createNTilesCard();
            createTileGridCard();
            createNPixelsPerTileCard();
          }
          if (memoryMap != null) createMemoryCard();

          tileModeSelector.setSelectedItem(
              (String)Prefs.get(
                  "unet_segmentation." + id + ".tilingOption",
                  (String)tileModeSelector.getSelectedItem()));

          weightFile = Prefs.get("unet_segmentation." + id + ".weightFile", "");
        }

  public void load() throws HDF5Exception
        {
          load(file);
        }

  public void save(File outputFile) throws HDF5Exception
        {
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
          writer.string().write("/model_prototxt", prototxt);
          writer.string().write("/unet_param/padding", padding);
          writer.int32().write("/unet_param/input_num_channels", nChannels);
          writer.int32().write(
              "/unet_param/normalization_type", normalizationType);
          writer.int32().writeArray(
              "/unet_param/downsampleFactor", downsampleFactor);
          writer.int32().writeArray("/unet_param/padInput", padInput);
          writer.int32().writeArray("/unet_param/padOutput", padOutput);
          writer.float32().writeArray(
              "/unet_param/element_size_um", elementSizeUm);
          if (memoryMap != null && memoryMap.length == 2)
              writer.int32().writeMatrix(
                  "/unet_param/mapInputNumPxGPUMemMB", memoryMap);
          writer.close();
        }

  public void save() throws HDF5Exception
        {
          save(file);
        }

  public void setFromTilingParameterString(String tilingParameter)
        {
          String[] arg = tilingParameter.split("=");
          if (arg[0].equals(NTILES))
          {
            tileModeSelector.setSelectedItem(NTILES);
            _nTilesSpinner.setValue(Integer.valueOf(arg[1]));
            return;
          }
          if (arg[0].equals(GRID))
          {
            tileModeSelector.setSelectedItem(GRID);
            String[] st = arg[1].split("x");
            if (st.length > 0) _gridXSpinner.setValue(Integer.valueOf(st[0]));
            else _gridXSpinner.setValue(1);
            if (st.length > 1) _gridYSpinner.setValue(Integer.valueOf(st[1]));
            else _gridYSpinner.setValue(1);
            if (elementSizeUm.length == 3)
            {
              if (st.length > 2) _gridZSpinner.setValue(Integer.valueOf(st[2]));
              else _gridZSpinner.setValue(1);
            }
            return;
          }
          if (arg[0].equals(SHAPE))
          {
            tileModeSelector.setSelectedItem(SHAPE);
            String[] st = arg[1].split("x");
            if (st.length > 0) _shapeXSpinner.setValue(Integer.valueOf(st[0]));
            else _shapeXSpinner.setValue(1);
            if (st.length > 1) _shapeYSpinner.setValue(Integer.valueOf(st[1]));
            else _shapeYSpinner.setValue(1);
            if (elementSizeUm.length == 3)
            {
              if (st.length > 2)
                  _shapeZSpinner.setValue(Integer.valueOf(st[2]));
              else _shapeZSpinner.setValue(1);
            }
            return;
          }
          if (arg[0].equals(NPIXELSPERTILE))
          {
            tileModeSelector.setSelectedItem(NPIXELSPERTILE);
            _nPixelsPerTileSpinner.setValue(Integer.valueOf(arg[1]));
            return;
          }
          if (arg[0].equals(MEMORY))
          {
            tileModeSelector.setSelectedItem(MEMORY);
            _gpuMemSpinner.setValue(Integer.valueOf(arg[1]));
            return;
          }
        }

  public String getTilingParameterString()
        {
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
          if (((String)tileModeSelector.getSelectedItem()).equals(
                  NPIXELSPERTILE))
              return NPIXELSPERTILE + "=" +
                  (Integer)_nPixelsPerTileSpinner.getValue();
          if (((String)tileModeSelector.getSelectedItem()).equals(MEMORY))
              return MEMORY + "=" + (Integer)_gpuMemSpinner.getValue();
          throw new UnsupportedOperationException(
              "Cannot handle unknown tiling mode '" +
              (String)tileModeSelector.getSelectedItem() + "'");
        }

  public String getCaffeTilingParameter()
      throws UnsupportedOperationException
        {
          if (((String)tileModeSelector.getSelectedItem()).equals(NTILES))
              return "-n_tiles " + (Integer)_nTilesSpinner.getValue();

          if (((String)tileModeSelector.getSelectedItem()).equals(GRID))
              return "-n_tiles " + ((elementSizeUm.length == 3) ?
                   ((Integer)_gridZSpinner.getValue() + "x") : "") +
                  (Integer)_gridYSpinner.getValue() + "x" +
                  (Integer)_gridXSpinner.getValue();
          if (((String)tileModeSelector.getSelectedItem()).equals(SHAPE))
              return "-tile_size " + ((elementSizeUm.length == 3) ?
                   ((Integer)_shapeZSpinner.getValue() + "x") : "") +
                  (Integer)_shapeYSpinner.getValue() + "x" +
                  (Integer)_shapeXSpinner.getValue();
          if (((String)tileModeSelector.getSelectedItem()).equals(
                  NPIXELSPERTILE))
              return "-mem_available_px " +
                  (Integer)_nPixelsPerTileSpinner.getValue();
          if (((String)tileModeSelector.getSelectedItem()).equals(MEMORY))
              return "-gpu_mem_available_MB " +
                  (Integer)_gpuMemSpinner.getValue();
          throw new UnsupportedOperationException(
              "Cannot handle unknown tiling mode '" +
              (String)tileModeSelector.getSelectedItem() + "'");
        }

  public void savePreferences()
        {
          Prefs.set("unet_segmentation." + id + ".weightFile", weightFile);

          Prefs.set("unet_segmentation." + id + ".tilingOption",
                    (String)tileModeSelector.getSelectedItem());

          if (((String)tileModeSelector.getSelectedItem()).equals(NTILES))
          {
            Prefs.set("unet_segmentation." + id + ".nTiles",
                      (Integer)_nTilesSpinner.getValue());
            return;
          }

          if (((String)tileModeSelector.getSelectedItem()).equals(GRID))
          {
            Prefs.set("unet_segmentation." + id + ".tileGridX",
                      (Integer)_gridXSpinner.getValue());
            Prefs.set("unet_segmentation." + id + ".tileGridY",
                      (Integer)_gridYSpinner.getValue());
            if (elementSizeUm.length == 3)
                Prefs.set("unet_segmentation." + id + ".tileGridZ",
                          (Integer)_gridZSpinner.getValue());
            return;
          }

          if (((String)tileModeSelector.getSelectedItem()).equals(SHAPE))
          {
            Prefs.set("unet_segmentation." + id + ".tileShapeX",
                      (Integer)_shapeXSpinner.getValue());
            Prefs.set("unet_segmentation." + id + ".tileShapeY",
                      (Integer)_shapeYSpinner.getValue());
            if (elementSizeUm.length == 3)
                Prefs.set("unet_segmentation." + id + ".tileShapeZ",
                          (Integer)_shapeZSpinner.getValue());
            return;
          }

          if (((String)tileModeSelector.getSelectedItem()).equals(
                  NPIXELSPERTILE))
          {
            Prefs.set("unet_segmentation." + id + ".nPixelsPerTile",
                      (Integer)_nPixelsPerTileSpinner.getValue());
            return;
          }

          if (((String)tileModeSelector.getSelectedItem()).equals(MEMORY))
          {
            Prefs.set("unet_segmentation." + id + ".GPUMemoryMB",
                      (Integer)_gpuMemSpinner.getValue());
            return;
          }

          throw new UnsupportedOperationException(
              "Cannot handle unknown tiling mode '" +
              (String)tileModeSelector.getSelectedItem() + "'");
        }

  @Override
  public String toString()
        {
          return name;
        }

};
