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

import caffe.Caffe;

/**
 * DataLayer provides functionality to compute the required
 * memory of the HDF5DataLayer or a generic InputLayer that has no input
 * blobs but produces output blobs. It is not strictly resembling a specific
 * caffe layer, but is a placeholder for any kind of data generation.
 *
 * @author Thorsten Falk
 * @version 1.0
 * @since 1.0
 */
public class DataLayer extends NetworkLayer {

  /**
   * Create a new <code>DataLayer</code> object.
   *
   * @param layerParam the parameters used to setup the layer in compiled
   *   protocol buffer format. The shapes of the output blobs are taken from
   *   the InputParameter values.
   * @param net the parent <code>Net</code> object
   *
   * @see caffe.Caffe.InputParameter
   */
  public DataLayer(Caffe.LayerParameter layerParam, Net net) {
    super(layerParam, net, null);
    for (int i = 0; i < layerParam.getTopCount(); ++i)
    {
      Caffe.BlobShape blobShape = layerParam.getInputParam().getShape(i);
      long[] shape = new long[blobShape.getDimCount()];
      for (int d = 0; d < blobShape.getDimCount(); ++d)
          shape[d] = blobShape.getDim(d);
      _out[i] = new CaffeBlob(layerParam.getTop(i), shape, this);
    }
  }

}
