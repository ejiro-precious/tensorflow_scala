/* Copyright 2017-18, Emmanouil Antonios Platanios. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.platanios.tensorflow.api.ops.io.data

import org.platanios.tensorflow.api.core.Shape
import org.platanios.tensorflow.api.io.{CompressionType, NoCompression}
import org.platanios.tensorflow.api.ops.{Basic, Op, Output}
import org.platanios.tensorflow.api.tensors.Tensor
import org.platanios.tensorflow.api.types.STRING

/** Dataset with elements read from TensorFlow record files.
  *
  * @param  filenames       Scalar or vector tensor containing the the name(s) of the file(s) to be read.
  * @param  compressionType Compression type for the file.
  * @param  bufferSize      Number of bytes to buffer while reading from the file.
  * @param  name            Name for this dataset.
  *
  * @author Emmanouil Antonios Platanios
  */
class TFRecordDataset protected (
    filenames: Tensor[STRING],
    compressionType: CompressionType = NoCompression,
    bufferSize: Long = 256 * 1024,
    override val name: String = "TFRecordDataset"
) extends Dataset[Tensor[STRING], Output, STRING, Shape](name) {
  if (filenames.rank != -1 && filenames.rank > 1)
    throw new IllegalArgumentException(s"'filenames' (rank = ${filenames.rank}) must be at most 1.")

  override def createHandle(): Output = {
    Op.Builder(opType = "TFRecordDataset", name = name)
        .addInput(Op.createWithNameScope(name)(filenames))
        .addInput(Op.createWithNameScope(name)(compressionType.name))
        .addInput(Op.createWithNameScope(name)(bufferSize))
        .build().outputs(0)
  }

  override def outputDataTypes: STRING = STRING
  override def outputShapes: Shape = Shape.scalar()
}

object TFRecordDataset {
  def apply(
      filenames: Tensor[STRING],
      compressionType: CompressionType = NoCompression,
      bufferSize: Long = 256 * 1024,
      numParallelReads: Int = 1,
      name: String = "TFRecordDataset"
  ): Dataset[Tensor[STRING], Output, STRING, Shape] = {
    if (numParallelReads == 1) {
      new TFRecordDataset(filenames, compressionType, bufferSize, name)
    } else {
      TensorSlicesDataset[Tensor[STRING], Output, STRING, Shape](filenames, name = s"$name/Filenames")
          .parallelInterleave(
            f => DynamicTFRecordDataset(f, compressionType, bufferSize, numParallelReads = 1, name = name),
            cycleLength = Basic.constant(numParallelReads, name = s"$name/NumParallelReads"),
            blockLength = Basic.constant(1, name = s"$name/BlockLength"),
            sloppy = false,
            name = s"$name/ParallelInterleave")
    }
  }
}

/** Dataset with elements read from TensorFlow record files.
  *
  * @param  filenames       Scalar or vector tensor containing the the name(s) of the file(s) to be read.
  * @param  compressionType Compression type for the file.
  * @param  bufferSize      Number of bytes to buffer while reading from the file.
  * @param  name            Name for this dataset.
  *
  * @author Emmanouil Antonios Platanios
  */
class DynamicTFRecordDataset protected (
    filenames: Output,
    compressionType: CompressionType = NoCompression,
    bufferSize: Long = 256 * 1024,
    override val name: String = "TFRecordDataset"
) extends Dataset[Tensor[STRING], Output, STRING, Shape](name) {
  if (filenames.rank != -1 && filenames.rank > 1)
    throw new IllegalArgumentException(s"'filenames' (rank = ${filenames.rank}) must be at most 1.")

  override def createHandle(): Output = {
    Op.Builder(opType = "TFRecordDataset", name = name)
        .addInput(filenames)
        .addInput(Op.createWithNameScope(name)(compressionType.name))
        .addInput(Op.createWithNameScope(name)(bufferSize))
        .build().outputs(0)
  }

  override def outputDataTypes: STRING = STRING
  override def outputShapes: Shape = Shape.scalar()
}

object DynamicTFRecordDataset {
  def apply(
      filenames: Output,
      compressionType: CompressionType = NoCompression,
      bufferSize: Long = 256 * 1024,
      numParallelReads: Int = 1,
      name: String = "TFRecordDataset"
  ): Dataset[Tensor[STRING], Output, STRING, Shape] = {
    if (numParallelReads == 1) {
      new DynamicTFRecordDataset(filenames, compressionType, bufferSize, name)
    } else {
      OutputSlicesDataset(filenames, name = s"$name/Filenames")
          .parallelInterleave(
            f => new DynamicTFRecordDataset(f, compressionType, bufferSize, name),
            cycleLength = Basic.constant(numParallelReads, name = s"$name/NumParallelReads"),
            blockLength = Basic.constant(1, name = s"$name/BlockLength"),
            sloppy = false,
            name = s"$name/ParallelInterleave")
    }
  }
}
