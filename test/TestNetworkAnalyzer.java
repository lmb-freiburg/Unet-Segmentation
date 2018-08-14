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

import de.unifreiburg.unet.ModelDefinition;
import de.unifreiburg.unet.Net;
import de.unifreiburg.unet.CaffeBlob;
import de.unifreiburg.unet.NotImplementedException;
import de.unifreiburg.unet.BlobException;

import caffe.Caffe;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;

import java.io.File;

public class TestNetworkAnalyzer {

  public static void main(String[] args) {

    if (args.length < 1) {
      System.out.println(
          "Usage: java -cp .:/home/falk/software/Fiji.app/jars/jhdf5-14.12.6." +
          "jar:/home/falk/software/Fiji.app/jars/protobuf-java-3.3.1.jar:" +
          "/home/falk/software/Fiji.app/jars/commons-io-2.6.jar:/home/falk" +
          "/software/Fiji.app/jars/ij-1.52e.jar TestNetworkAnalyzer " +
          "<inputEdgeLength>");
      System.exit(1);
    }

    int inputEdgeLength = Integer.parseInt(args[0]);
    try
    {
      ModelDefinition model = new ModelDefinition();
      model.load(
          new File("/home/falk/tmp/test.modeldef.h5"));
      Caffe.NetParameter.Builder netParamBuilder =
          Caffe.NetParameter.newBuilder();
      TextFormat.getParser().merge(model.modelPrototxt, netParamBuilder);

      Net net = Net.createFromProto(
          netParamBuilder.build(), new String[] { "data3" },
          new long[][] {
              new long[] { 1, 1, inputEdgeLength, inputEdgeLength } },
          Caffe.Phase.TEST);

      System.out.println(net);

      System.out.println(" === TEST === ");
      net.printMemoryBreakdown(false);
      net.printMemoryBreakdown(true);
      System.out.print("Output blobs: ");
      for (CaffeBlob blob : net.outputBlobs()) System.out.print(blob + " ");
      System.out.println();

      net = Net.createFromProto(
          netParamBuilder.build(), null,
          new long[][] {
              new long[] { 1, 1, inputEdgeLength, inputEdgeLength } },
          Caffe.Phase.TRAIN);

      System.out.println(" === TRAIN === ");
      net.printMemoryBreakdown(false);
      net.printMemoryBreakdown(true);
      System.out.print("Output blobs: ");
      for (CaffeBlob blob : net.outputBlobs()) System.out.print(blob + " ");
      System.out.println();
    }
    catch (ParseException e) {
      System.err.println("Could not parse model prototxt" + e.getMessage());
    }
    catch (NotImplementedException e) {
      System.err.println("Could not create Net: " + e.getMessage());
    }
    catch (BlobException e) {
      System.err.println("Could not create Net: " + e.getMessage());
    }
  }

}
