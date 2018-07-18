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
import ij.gui.ImageRoi;
import ij.gui.Overlay;

public class MaskEmbedder implements PlugIn {

  public static void embed(
      ImagePlus target, ImagePlus masks)
      throws IncompatibleImagesException {
    if (target.getNFrames() != masks.getNFrames() ||
        target.getNSlices() != masks.getNSlices() ||
        target.getHeight() != masks.getHeight() ||
        target.getWidth() != masks.getWidth())
        throw new IncompatibleImagesException(
            "Target and mask images must have the same number of frames, " +
            "number of slices and image size.");
    for (int t = 1; t <= target.getNFrames(); ++t) {
      for (int z = 1; z <= target.getNSlices(); ++z) {
        ImageRoi roi = new ImageRoi(
            0, 0, masks.getStack().getProcessor(
                masks.getStackIndex(1, z, t)).duplicate());
        roi.setPosition(1, z, t);
        roi.setPosition(target.getStackIndex(1, z, t));
        roi.setOpacity(0.3);
        if (target.getOverlay() == null) target.setOverlay(new Overlay());
        target.getOverlay().add(roi, "label mask");
      }
    }
  }

  @Override
  public void run(String arg) {
    try {
      TrainImagePair pair = TrainImagePair.selectImagePair();
      embed(pair.rawdata(), pair.rawlabels());
    }
    catch (IncompatibleImagesException e) {
      IJ.error("Invalid image pair: " + e);
    }
  }

}
