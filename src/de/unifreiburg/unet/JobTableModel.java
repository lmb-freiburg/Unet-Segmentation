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
import ij.ImagePlus;
import ij.WindowManager;

import javax.swing.table.AbstractTableModel;
import javax.swing.JOptionPane;
import java.util.Vector;

public class JobTableModel extends AbstractTableModel {

  private String[] _columnNames = {
      "Job ID", "Source Image", "Model", "Weights", "Host", "Status",
      "Progress", "Show" };

  private Vector<Job> _jobs;

  public JobTableModel() {
    _jobs = new Vector<Job>();
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
      return (_jobs.get(row).sshSession() != null) ?
          _jobs.get(row).sshSession().getHost() : "localhost";
    }
    case 5: {
      return _jobs.get(row).progressMonitor();
    }
    case 6: {
      return new Float(_jobs.get(row).progressMonitor().progress());
    }
    case 7: {
      return _jobs.get(row).readyCancelButton();
    }
    default: {
      return null;
    }
    }
  }

  public void deleteJob(Job job) {
    if (job == null) return;
    int jobIdx = 0;
    while (jobIdx < _jobs.size() && _jobs.get(jobIdx) != job) ++jobIdx;
    if (jobIdx == _jobs.size()) return;
    if (_jobs.get(jobIdx).isAlive()) _jobs.get(jobIdx).interrupt();
    _jobs.remove(jobIdx);
    fireTableRowsDeleted(jobIdx, jobIdx);
  }

  public void createSegmentationJob() {
    startJob(new SegmentationJob(this));
  }

  public void createDetectionJob() {
    startJob(new DetectionJob(this));
  }

  public void createFinetuneJob() {
    startJob(new FinetuneJob(this));
  }

  private void startJob(Job job) {
    if (WindowManager.getCurrentImage() == null) {
      IJ.noImage();
      return;
    }
    _jobs.add(job);
    job.start();
    fireTableRowsInserted(_jobs.size() - 1, _jobs.size() - 1);
  }

  public Job job(String jobId) {
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
