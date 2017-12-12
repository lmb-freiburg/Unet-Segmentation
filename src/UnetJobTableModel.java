import ij.*;

import javax.swing.table.*;
import java.util.Vector;

public class UnetJobTableModel extends AbstractTableModel {

  private String[] _columnNames = {
      "Job ID", "Source Image", "Model", "Weights", "Host", "Status",
      "Progress", "Show" };

  private Vector<UnetJob> _jobs;

  public UnetJobTableModel() {
    _jobs = new Vector<UnetJob>();
  }

  @Override
  public String getColumnName(int col) {
    if (col < _columnNames.length)
        return _columnNames[col].toString();
    else return "";
  }

  @Override
  public int getColumnCount() {
    return _columnNames.length;
  }

  @Override
  public int getRowCount() {
    return _jobs.size();
  }

  public Class getColumnClass(int column) {
    if (_jobs.size() == 0) return new String().getClass();
    return getValueAt(0, column).getClass();
  }

  @Override
  public Object getValueAt(int row, int col) {
    if (row >= _jobs.size()) return null;
    switch (col) {
    case 0: {
      return _jobs.get(row).id();
    }
    case 1: {
      return _jobs.get(row).imageName();
    }
    case 2: {
      return (_jobs.get(row).model() != null) ?
          _jobs.get(row).model().name : "<no model>";
    }
    case 3: {
      return _jobs.get(row).weightsFileName();
    }
    case 4: {
      return _jobs.get(row).hostname();
    }
    case 5: {
      return _jobs.get(row).status();
    }
    case 6: {
      return (int)_jobs.get(row).progress();
    }
    case 7: {
      return _jobs.get(row).readyCancelButton();
    }
    default: {
      return null;
    }
    }
  }

  public void deleteJob(UnetJob job) {
    if (job == null) return;
    int jobIdx = 0;
    while (jobIdx < _jobs.size() && _jobs.get(jobIdx) != job) ++jobIdx;
    if (jobIdx == _jobs.size()) return;
    if (_jobs.get(jobIdx).isAlive()) _jobs.get(jobIdx).interrupt();
    _jobs.remove(jobIdx);
    fireTableRowsDeleted(jobIdx, jobIdx);
  }

  // public void createFinetuneJob() {
  //   UnetFinetuneJob job = new UnetFinetuneJob();
  //   job.setJobTableModel(this);
  //   job.prepareParametersDialog();
  //   _jobs.add(job);
  //   job.start();
  //   fireTableRowsInserted(_jobs.size() - 1, _jobs.size() - 1);
  // }

  public void createSegmentationJob(ImagePlus imp) {
    if (imp == null) {
      IJ.error(
          "U-Net Segmentation", "No image selected for segmentation.");
      return;
    }
    UnetSegmentationJob job = new UnetSegmentationJob();
    job.setJobTableModel(this);
    job.setImagePlus(imp);
    job.prepareParametersDialog();
    _jobs.add(job);
    job.start();
    fireTableRowsInserted(_jobs.size() - 1, _jobs.size() - 1);
  }

  public UnetJob getJob(String jobId) {
    int jobIdx = 0;
    while (jobIdx < _jobs.size() && !_jobs.get(jobIdx).id().equals(jobId))
        ++jobIdx;
    if (jobIdx == _jobs.size()) return null;
    return _jobs.get(jobIdx);
  }

  public void updateJobStatus(String jobId) {
    int jobIdx = 0;
    while (jobIdx < _jobs.size() && !_jobs.get(jobIdx).id().equals(jobId))
        ++jobIdx;
    if (jobIdx == _jobs.size()) return;
    fireTableCellUpdated(jobIdx, 5);
  }

  public void updateJobProgress(String jobId) {
    int jobIdx = 0;
    while (jobIdx < _jobs.size() && !_jobs.get(jobIdx).id().equals(jobId))
        ++jobIdx;
    if (jobIdx == _jobs.size()) return;
    fireTableCellUpdated(jobIdx, 6);
  }

  public void updateJobDownloadEnabled(String jobId) {
    int jobIdx = 0;
    while (jobIdx < _jobs.size() && !_jobs.get(jobIdx).id().equals(jobId))
        ++jobIdx;
    if (jobIdx == _jobs.size()) return;
    fireTableCellUpdated(jobIdx, 7);
  }

  public void cancelJob(String jobId) {
    int jobIdx = 0;
    while (jobIdx < _jobs.size() && !_jobs.get(jobIdx).id().equals(jobId))
        ++jobIdx;
    if (jobIdx == _jobs.size()) return;
    if (_jobs.get(jobIdx).isAlive()) _jobs.get(jobIdx).interrupt();
  }

  public void showAndDequeueJob(String jobId) {
    int jobIdx = 0;
    while (jobIdx < _jobs.size() && !_jobs.get(jobIdx).id().equals(jobId))
        ++jobIdx;
    if (jobIdx == _jobs.size()) return;
    _jobs.get(jobIdx).finish();
  }

};
