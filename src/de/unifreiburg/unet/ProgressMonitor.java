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

import com.jcraft.jsch.SftpProgressMonitor;

import ij.IJ;

import javax.swing.SwingUtilities;

public class ProgressMonitor implements SftpProgressMonitor {

  private final Job _job;

  // Task specific progress
  private String _message = "";
  private long _reportedCount = 0;
  private long _count = 0;
  private long _max = 0;

  // The lower and upper bounds of the current task with respect to
  // the overall progress
  private float _taskProgressMin = 0.0f;
  private float _taskProgressMax = 0.0f;

  // The overall progress of the job in [0, 1]
  private float _jobProgress = 0.0f;

  // Flag indicating that the job was canceled by the user
  private boolean _canceled = false;

  // Flag indicating that the job is finished
  private boolean _finished = false;

  public ProgressMonitor(Job job) {
    _job = job;
  }

  // This must be called whenever a new task starts!
  public void initNewTask(String message, float taskProgressMax, long max) {
    _message = message;
    _taskProgressMin = _taskProgressMax;
    _taskProgressMax = taskProgressMax;
    _count = 0;
    _reportedCount = 0;
    _max = max;
    update();
  }

  public void reset() {
    _message = "";
    _reportedCount = 0;
    _count = 0;
    _max = 0;
    _taskProgressMin = 0.0f;
    _taskProgressMax = 0.0f;
    _jobProgress = 0.0f;
    _canceled = false;
    _finished = false;
  }

  // This is called when using the ProgressMonitor in an Sftp operation
  // It does not set the taskProgressMax, so please call initNewTask before
  // passing the ProgressMonitor to the Sftp operation.
  @Override
  public void init(int op, String src, String dest, long max) {
    _count = 0;
    _reportedCount = 0;
    _max = max;
    update();
  }

  @Override
  public boolean count(long count) {
    _count += count;
    _jobProgress = _taskProgressMin + (
        (_max > 0) ? ((float)_count / (float)_max *
                      (_taskProgressMax - _taskProgressMin)) : 0.0f);
    update();
    if (_job.interrupted()) setCanceled(true);
    return !_canceled;
  }

  public boolean count(String message, long count) {
    _message = message;
    return this.count(count);
  }

  @Override
  public void end() {
    _count = _max;
  }

  public String message() {
    return _message;
  }

  public long getCount() {
    return _count;
  }

  public long getMax() {
    return _max;
  }

  public float taskProgressMin() {
    return _taskProgressMin;
  }

  public float taskProgressMax() {
    return _taskProgressMax;
  }

  public float progress() {
    return _jobProgress;
  }

  public void setCanceled(boolean canceled) {
    _canceled = canceled;
    if (canceled) _finished = _canceled;
    end();
  }

  public boolean canceled() {
    return _canceled;
  }

  public void setFinished(boolean finished) {
    _finished = finished;
    end();
  }

  public boolean finished() {
    return _finished;
  }

  public void update() {
    // If current percentage was already reported, do not show new progress
    if((int)(10000 * (float)_reportedCount / (float)_max) >=
       (int)(10000 * (float)_count / (float)_max)) return;
    _reportedCount = _count;
    if (_job != null && _job.jobTable() != null) {
      SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              _job.jobTable().updateJobStatus(_job.id());
              _job.jobTable().updateJobProgress(_job.id());
            }});
    }
    else {
      IJ.showStatus(_message);
      IJ.showProgress(_jobProgress);
    }
  }

};
