lazy val checkDirectFiltersTransitive = taskKey[Unit]("Direct mode: transitive internal dep filtered out")
lazy val checkDirectKeepsDirect = taskKey[Unit]("Direct mode: direct internal dep kept")
lazy val checkPlusOneKeepsDirect = taskKey[Unit]("PlusOne mode: direct internal dep kept")
lazy val checkPlusOneKeepsPlusOne = taskKey[Unit]("PlusOne mode: plus-one internal dep kept")
lazy val checkPlusOneFiltersDeep = taskKey[Unit]("PlusOne mode: deep transitive internal dep filtered")
lazy val checkTransitiveKeepsAll = taskKey[Unit]("Transitive mode: all internal deps present")

lazy val Core = (project in file("Core"))
  .settings(scalaVersion := "3.7.4")

lazy val LibA = (project in file("LibA"))
  .dependsOn(Core)
  .settings(scalaVersion := "3.7.4")

lazy val LibB = (project in file("LibB"))
  .dependsOn(LibA) // intentionally NOT depending on Core
  .settings(
    scalaVersion := "3.7.4",
    checkTransitiveKeepsAll := {
      val filtered = (Compile / filteredDependencyClasspath).value
      val moduleIds = filtered.flatMap(_.get(moduleIDStr))
      assert(moduleIds.exists(_.contains("\"name\":\"liba\"")),
        s"Expected liba in transitive internal classpath, got moduleIDs: $moduleIds")
      assert(moduleIds.exists(_.contains("\"name\":\"core\"")),
        s"Expected core in transitive internal classpath, got moduleIDs: $moduleIds")
    },
    checkDirectKeepsDirect := {
      val filtered = (Compile / filteredDependencyClasspath).value
      val moduleIds = filtered.flatMap(_.get(moduleIDStr))
      assert(moduleIds.exists(_.contains("\"name\":\"liba\"")),
        s"Expected liba in direct mode filtered classpath, got moduleIDs: $moduleIds")
    },
    checkDirectFiltersTransitive := {
      val filtered = (Compile / filteredDependencyClasspath).value
      val moduleIds = filtered.flatMap(_.get(moduleIDStr))
      assert(!moduleIds.exists(_.contains("\"name\":\"core\"")),
        s"Expected no core in direct mode filtered classpath (transitive dep), got moduleIDs: $moduleIds")
    },
    checkPlusOneKeepsDirect := {
      val filtered = (Compile / filteredDependencyClasspath).value
      val moduleIds = filtered.flatMap(_.get(moduleIDStr))
      assert(moduleIds.exists(_.contains("\"name\":\"liba\"")),
        s"Expected liba in plusOne mode filtered classpath, got moduleIDs: $moduleIds")
    },
    checkPlusOneKeepsPlusOne := {
      // Core is a direct dep of LibA (which is a direct dep of LibB) => plus-one
      val filtered = (Compile / filteredDependencyClasspath).value
      val moduleIds = filtered.flatMap(_.get(moduleIDStr))
      assert(moduleIds.exists(_.contains("\"name\":\"core\"")),
        s"Expected core (plus-one internal dep) in plusOne mode filtered classpath, got moduleIDs: $moduleIds")
    },
  )

// Deep chain: LibD -> LibC -> LibB -> LibA -> Core
// Under PlusOne from LibD's perspective: LibC is direct, LibB is plus-one, LibA/Core are deep
lazy val LibC = (project in file("LibC"))
  .dependsOn(LibB)
  .settings(scalaVersion := "3.7.4")

lazy val LibD = (project in file("LibD"))
  .dependsOn(LibC)
  .settings(
    scalaVersion := "3.7.4",
    checkPlusOneFiltersDeep := {
      val filtered = (Compile / filteredDependencyClasspath).value
      val moduleIds = filtered.flatMap(_.get(moduleIDStr))
      // LibC is direct dep -> kept
      assert(moduleIds.exists(_.contains("\"name\":\"libc\"")),
        s"Expected libc in plusOne mode, got moduleIDs: $moduleIds")
      // LibB is plus-one (direct dep of LibC) -> kept
      assert(moduleIds.exists(_.contains("\"name\":\"libb\"")),
        s"Expected libb (plus-one) in plusOne mode, got moduleIDs: $moduleIds")
      // LibA is two levels deep -> filtered
      assert(!moduleIds.exists(_.contains("\"name\":\"liba\"")),
        s"Expected no liba (deep transitive) in plusOne mode, got moduleIDs: $moduleIds")
      // Core is three levels deep -> filtered
      assert(!moduleIds.exists(_.contains("\"name\":\"core\"")),
        s"Expected no core (deep transitive) in plusOne mode, got moduleIDs: $moduleIds")
    },
  )
