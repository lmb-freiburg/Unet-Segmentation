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
import ij.WindowManager;
import ij.Prefs;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.io.File;
import java.io.FileFilter;

public class ResumeFinetuning extends Thread implements PlugIn {

  @Override
  public void run(String arg) {
    start();
  }

  @Override
  public void run() {
    JFileChooser f = new JFileChooser(
        new File(Prefs.get("unet.finetuning.resumefolder", ".")));
    f.setDialogTitle("Select U-Net snapshot");
    f.setMultiSelectionEnabled(false);
    f.setFileSelectionMode(JFileChooser.FILES_ONLY);
    f.setFileFilter(new FileNameExtensionFilter("HDF5 files", "h5", "H5"));
    int res = f.showDialog(WindowManager.getActiveWindow(), "Select");
    if (res != JFileChooser.APPROVE_OPTION) return;
    Prefs.set("unet.finetuning.resumefolder",
              f.getSelectedFile().getParentFile().getAbsolutePath());
    new FinetuneJob().resumeFinetuning(f.getSelectedFile());
  }

}
