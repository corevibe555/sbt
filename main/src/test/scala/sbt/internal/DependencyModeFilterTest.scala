/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.internal.util.Attributed
import _root_.sbt.librarymanagement.{
  Configuration,
  Configurations,
  ModuleID,
  ScalaArtifacts,
  UpdateReport,
  UpdateStats,
}
import xsbti.HashedVirtualFileRef

object DependencyModeFilterTest extends Properties:

  private def makeModuleID(org: String, n: String): ModuleID =
    ModuleID(org, n, "1.0.0")

  /** Create a classpath entry tagged with the given ModuleID. */
  private def cpEntry(org: String, n: String): Attributed[HashedVirtualFileRef] =
    val mid = makeModuleID(org, n)
    val entryName = n
    val entryOrg = org
    val ref = new HashedVirtualFileRef {
      def id(): String = s"$entryOrg/$entryName-1.0.0.jar"
      def name(): String = s"$entryName-1.0.0.jar"
      def contentHashStr(): String = "deadbeef"
      def names(): Array[String] = Array(s"$entryName-1.0.0.jar")
      def sizeBytes(): Long = 0L
    }
    Attributed
      .blank(ref)
      .put(Keys.moduleIDStr, Classpaths.moduleIdJsonKeyFormat.write(mid))

  /** Create a classpath entry without moduleIDStr metadata. */
  private def cpEntryNoMeta(entryId: String): Attributed[HashedVirtualFileRef] =
    val ref = new HashedVirtualFileRef {
      def id(): String = entryId
      def name(): String = entryId
      def contentHashStr(): String = "deadbeef"
      def names(): Array[String] = Array(entryId)
      def sizeBytes(): Long = 0L
    }
    Attributed.blank(ref)

  private def hasEntry(cp: Seq[Attributed[HashedVirtualFileRef]], n: String): Boolean =
    cp.exists(_.data.id().contains(n))

  private val emptyReport = UpdateReport(
    java.io.File.createTempFile("dummy", ".xml"),
    Vector.empty,
    UpdateStats(0L, 0L, 0L, false),
    Map.empty,
  )

  private val compileConfig: Configuration = Configurations.Compile

  override def tests: List[Test] = List(
    // -- filterByDirectDeps: external deps --
    example("filterByDirectDeps: keeps direct deps, filters transitive", {
      val directDeps = Seq(makeModuleID("org.typelevel", "cats-core"))
      val jars = Seq(
        cpEntry("org.typelevel", "cats-core_3"),
        cpEntry("org.typelevel", "cats-kernel_3"),
      )
      val result = ClasspathImpl.filterByDirectDeps(directDeps, jars)
      Result.all(List(
        Result.assert(hasEntry(result, "cats-core_3")).log("direct dep should be kept"),
        Result.assert(!hasEntry(result, "cats-kernel_3")).log("transitive dep should be filtered"),
      ))
    }),
    example("filterByDirectDeps: keeps scala-library and scala3-library", {
      val directDeps = Seq(makeModuleID("com.example", "app"))
      val jars = Seq(
        cpEntry(ScalaArtifacts.Organization, ScalaArtifacts.LibraryID),
        cpEntry(ScalaArtifacts.Organization, ScalaArtifacts.Scala3LibraryID),
      )
      val result = ClasspathImpl.filterByDirectDeps(directDeps, jars)
      Result.all(List(
        Result.assert(hasEntry(result, ScalaArtifacts.LibraryID))
          .log("scala-library should be kept"),
        Result.assert(hasEntry(result, ScalaArtifacts.Scala3LibraryID))
          .log("scala3-library should be kept"),
      ))
    }),
    example("filterByDirectDeps: entries without moduleIDStr pass through", {
      val directDeps = Seq(makeModuleID("com.example", "libA"))
      val jars = Seq(cpEntryNoMeta("unmanagedDir"))
      val result = ClasspathImpl.filterByDirectDeps(directDeps, jars)
      Result.assert(hasEntry(result, "unmanagedDir")).log("untagged entries should pass through")
    }),

    // -- filterByDirectDeps: inter-project deps --
    example(
      "filterByDirectDeps: filters transitive internal dep",
      {
        val directDeps = Seq(makeModuleID("default", "LibA"))
        val jars = Seq(
          cpEntry("default", "LibA"),
          cpEntry("default", "Core"),
        )
        val result = ClasspathImpl.filterByDirectDeps(directDeps, jars)
        Result.all(List(
          Result.assert(hasEntry(result, "LibA")).log("direct internal dep should be kept"),
          Result.assert(!hasEntry(result, "Core")).log("transitive internal dep should be filtered"),
        ))
      },
    ),
    example(
      "filterByDirectDeps: keeps multiple direct internal deps",
      {
        val directDeps = Seq(
          makeModuleID("com.example", "LibA"),
          makeModuleID("com.example", "Core"),
        )
        val jars = Seq(
          cpEntry("com.example", "LibA"),
          cpEntry("com.example", "Core"),
          cpEntry("com.example", "Utils"),
        )
        val result = ClasspathImpl.filterByDirectDeps(directDeps, jars)
        Result.all(List(
          Result.assert(hasEntry(result, "LibA")).log("LibA should be kept"),
          Result.assert(hasEntry(result, "Core")).log("Core should be kept"),
          Result.assert(!hasEntry(result, "Utils")).log("undeclared Utils should be filtered"),
        ))
      },
    ),

    // -- filterByPlusOne: inter-project deps --
    example(
      "filterByPlusOne: keeps internal deps in allowedInternalIds",
      {
        val directDeps = Seq(makeModuleID("default", "LibA"))
        val projectId = makeModuleID("default", "LibB")
        val jars = Seq(
          cpEntry("default", "LibA"),
          cpEntry("default", "Core"),
        )
        val allowedInternalIds = Seq(
          makeModuleID("default", "LibA"),
          makeModuleID("default", "Core"),
        )
        val result = ClasspathImpl.filterByPlusOne(
          directDeps, projectId, compileConfig, emptyReport, jars, allowedInternalIds
        )
        Result.all(List(
          Result.assert(hasEntry(result, "LibA")).log("direct internal dep should be kept"),
          Result.assert(hasEntry(result, "Core")).log("plus-one internal dep should be kept"),
        ))
      },
    ),
    example(
      "filterByPlusOne: filters deep internal deps not in allowedInternalIds",
      {
        val directDeps = Seq(makeModuleID("default", "LibC"))
        val projectId = makeModuleID("default", "LibD")
        val jars = Seq(
          cpEntry("default", "LibC"),
          cpEntry("default", "LibB"),
          cpEntry("default", "LibA"),
          cpEntry("default", "Core"),
        )
        val allowedInternalIds = Seq(
          makeModuleID("default", "LibC"),
          makeModuleID("default", "LibB"),
        )
        val result = ClasspathImpl.filterByPlusOne(
          directDeps, projectId, compileConfig, emptyReport, jars, allowedInternalIds
        )
        Result.all(List(
          Result.assert(hasEntry(result, "LibC")).log("direct dep should be kept"),
          Result.assert(hasEntry(result, "LibB")).log("plus-one dep should be kept"),
          Result.assert(!hasEntry(result, "LibA")).log("deep dep LibA should be filtered"),
          Result.assert(!hasEntry(result, "Core")).log("deep dep Core should be filtered"),
        ))
      },
    ),
    example(
      "filterByPlusOne: entries without moduleIDStr pass through",
      {
        val directDeps = Seq(makeModuleID("default", "LibA"))
        val projectId = makeModuleID("default", "LibB")
        val jars = Seq(cpEntryNoMeta("some-unmanaged.jar"))
        val allowedInternalIds = Seq.empty[ModuleID]
        val result = ClasspathImpl.filterByPlusOne(
          directDeps, projectId, compileConfig, emptyReport, jars, allowedInternalIds
        )
        Result.assert(hasEntry(result, "some-unmanaged.jar"))
          .log("untagged entries should pass through")
      },
    ),
  )
end DependencyModeFilterTest
