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
import ij.plugin.PlugIn;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.gui.Roi;
import ij.gui.ImageRoi;
import ij.gui.Overlay;

public class MaskExtractor implements PlugIn {

  public static ImagePlus extract(ImagePlus imp) {
    if (imp == null) {
      IJ.noImage();
      return null;
    }
    Overlay ov = imp.getOverlay();
    if (ov == null) {
      IJ.error("The selected image does not contain annotations.");
      return null;
    }
    ImagePlus labels = IJ.createHyperStack(
        imp.getTitle() + " - labels", imp.getWidth(), imp.getHeight(), 1,
        imp.getNSlices(), imp.getNFrames(), 16);
    labels.setCalibration(imp.getCalibration());
    for (Roi roi : ov.toArray()) {
      if (!(roi instanceof ImageRoi)) continue;
      int tRoi = 1;
      int zRoi = 1;
      if (roi.getPosition() != 0)
      {
        int[] pos = imp.convertIndexToPosition(roi.getPosition());
        tRoi = pos[2];
        zRoi = pos[1];
      }
      else {
        if (roi.getTPosition() != 0) tRoi = roi.getTPosition();
        if (roi.getZPosition() != 0) zRoi = roi.getZPosition();
      }
      labels.getStack().setProcessor(
          ((ImageRoi)roi).getProcessor().convertToShort(false),
          labels.getStackIndex(1, zRoi, tRoi));
    }
    return labels;
  }

  @Override
  public void run(String arg) {
    ImagePlus labelImp = extract(IJ.getImage());
    if (labelImp != null) {
      labelImp.show();
      labelImp.updateAndDraw();
    }
  }

}
