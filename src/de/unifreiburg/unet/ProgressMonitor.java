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

import java.lang.System;

public class ProgressMonitor implements SftpProgressMonitor {

  private final Job _job;
  private TaskMonitor _currentTask = new TaskMonitor();
  private long _reportedCount = 0;
  private String _reportedMessage = "";
  private long _reportedTimeNano = System.nanoTime();
  private boolean _canceled = false;

  public class TaskMonitor implements SftpProgressMonitor {

    private TaskMonitor _parent = null;
    private float _pMin = 0.0f, _pMax = 1.0f;
    private long _count = 0, _max = 0;
    private String _msg = null;

    public TaskMonitor() {}

    public TaskMonitor(float pMin, float pMax) {
      this._pMin = pMin;
      this._pMax = pMax;
    }

    public TaskMonitor(String msg, float pMin, float pMax) {
      this._msg = msg;
      this._pMin = pMin;
      this._pMax = pMax;
    }

    public TaskMonitor(TaskMonitor parent, float pMin, float pMax) {
      this._parent = parent;
      this._pMin = pMin;
      this._pMax = pMax;
    }

    public TaskMonitor getParent() {
      return _parent;
    }

    public String message() {
      return (_msg != null) ?
          _msg : ((_parent != null) ? _parent.message() : "");
    }

    public void init(long max) {
      this._count = 0;
      this._max = max;
      update(true);
    }

    @Override
    public void init(int op, String src, String dest, long max) {
      this._msg = "Copying " + src + " -> " + dest;
      this._count = 0;
      this._max = max;
      update(true);
    }

    @Override
    public boolean count(long count) {
      _count += count;
      update();
      return !_job.interrupted();
    }

    public boolean count(String msg, long count) {
      _msg = msg;
      return this.count(count);
    }

    @Override
    public void end() {
      _count = _max;
      update(true);
    }

    public long getCount() {
      return _count;
    }

    public long getMax() {
      return _max;
    }

    private float progress(float p) {
      if (_parent == null) return p;
      return _parent.progress(p * (_pMax - _pMin) + _pMin);
    }

    public float progress() {
      return (_max != 0) ? progress((float)_count / (float)_max) : 0.0f;
    }

    private float totalProgress(float p) {
      if (_parent == null) return p * (_pMax - _pMin) + _pMin;
      return _parent.progress(p);
    }

    public float totalProgress() {
      return totalProgress(progress());
    }

  }

  public ProgressMonitor(Job job) {
    _job = job;
  }

  public TaskMonitor getCurrentTaskMonitor() {
    return _currentTask;
  }

  public void reset() {
    _currentTask = new TaskMonitor();
    _reportedCount = 0;
    _reportedMessage = "";
    _reportedTimeNano = System.nanoTime();
    _canceled = false;
  }

  public void push(String message, float pMin, float pMax) {
    _currentTask = new TaskMonitor(_currentTask, pMin, pMax);
    _currentTask.count(message, 0);
    update(true);
  }

  public void push(float pMin, float pMax) {
    _currentTask = new TaskMonitor(_currentTask, pMin, pMax);
    update(true);
  }

  public void pop() {
    if (_currentTask != null) _currentTask = _currentTask.getParent();
  }

  public void setCanceled(boolean canceled) {
    _canceled = canceled;
  }

  public boolean canceled() {
    return _canceled;
  }

  public void setFinished(boolean finished) {
    if (!finished && _currentTask == null) reset();
    if (finished && _currentTask != null) _currentTask = null;
  }

  public boolean finished() {
    return _currentTask == null;
  }

  @Override
  public void init(int op, String src, String dest, long max) {
    if (_currentTask != null) _currentTask.init(op, src, dest, max);
  }

  public void init(long max) {
    if (_currentTask != null) _currentTask.init(max);
  }

  public boolean count(String msg, long count) {
    if (_currentTask != null) return _currentTask.count(msg, count);
    return false;
  }

  @Override
  public boolean count(long count) {
    if (_currentTask != null) return _currentTask.count(count);
    return false;
  }

  @Override
  public void end() {
    if (_currentTask != null) _currentTask.end();
  }

  public String message() {
    return (_currentTask != null) ? _currentTask.message() : "";
  }

  public long getCount() {
    return (_currentTask != null) ? _currentTask.getCount() : 0;
  }

  public long getMax() {
    return (_currentTask != null) ? _currentTask.getMax() : 0;
  }

  public float taskProgress() {
    return (_currentTask != null) ? _currentTask.progress() : 1;
  }

  public float progress() {
    return (_currentTask != null) ? _currentTask.totalProgress() : 1;
  }

  public void update() {
    update(false);
  }


  public void update(boolean immediate) {
    // If current percentage was already reported, do not show new progress
    if (!immediate && System.nanoTime() - _reportedTimeNano < 100000) return;
    if (message().equals(_reportedMessage) &&
        (int)(10000 * (float)_reportedCount / (float)getMax()) >=
        (int)(10000 * (float)getCount() / (float)getMax())) return;
    _reportedTimeNano = System.nanoTime();
    _reportedMessage = message();
    _reportedCount = getCount();
    if (_job != null && _job.jobTable() != null) {
      SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              _job.jobTable().updateJobStatus(_job.id());
              _job.jobTable().updateJobProgress(_job.id());
            }});
    }
    else {
      IJ.showStatus(message());
      IJ.showProgress(progress());
    }
  }

};
