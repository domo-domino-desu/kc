package ddd.kc.desktop.e2e

/** One scenario per primary navigation, then complete artist/post page-family workflows. */
internal fun onlineScenarios(ui: TaoUiDriver): LinkedHashMap<String, suspend () -> Unit> =
    linkedMapOf<String, suspend () -> Unit>(
            "nav-creators" to
                {
                  ui.click("nav-creators")
                  ui.get("creator-card")
                  ui.pagination()
                  val anchor = ui.scroll()
                  ui.click("creator-card")
                  ui.get("detail-pager")
                  ui.back()
                  ui.until("creator-list-restored") { ui.state("browse-list") == anchor }
                },
            "nav-works" to
                {
                  popular(ui)
                  ui.pagination()
                  postRoundtrip(ui)
                  postSearch(ui)
                  ui.pagination()
                  postRoundtrip(ui)
                  ui.clickLabel("Tags", "标签")
                  ui.click("tag-card")
                  ui.get("post-card")
                  ui.pagination()
                  postRoundtrip(ui)
                  ui.back()
                },
            "nav-dm" to
                {
                  ui.click("nav-dm")
                  ui.type("search-input", "")
                  ui.click("search-submit")
                  ui.get("dm-card")
                  ui.scroll()
                  ui.scroll(down = false)
                  ui.pagination()
                  dmSearch(ui)
                  ui.pagination()
                },
            "nav-more" to
                {
                  // Real UI setup makes histories deterministic even on a fresh isolated profile.
                  ui.click("nav-creators")
                  ui.click("creator-card")
                  ui.get("detail-pager")
                  ui.back()
                  popular(ui)
                  ui.click("post-card")
                  ui.get("detail-pager")
                  ui.back()
                  postSearch(ui)
                  dmSearch(ui)
                  ui.click("nav-more")
                  ui.click("more-history")
                  ui.click("creator-card")
                  ui.get("detail-pager")
                  ui.back()
                  ui.clickLabel("Posts")
                  ui.click("post-card")
                  ui.get("detail-pager")
                  ui.back()
                  ui.back()
                  ui.click("more-search-history")
                  ui.click("search-history-posts")
                  ui.get("post-card")
                  ui.click("nav-more")
                  ui.click("more-search-history")
                  ui.clickLabel("DMs", "私信")
                  ui.click("search-history-dms")
                  ui.get("dm-card")
                  ui.click("nav-more")
                  ui.click("more-settings")
                  ui.get("settings-screen")
                  ui.clickLabel("Cancel", "取消")
                  ui.click("more-about")
                  ui.get("about-screen")
                  ui.back()
                },
            "artist-page" to
                {
                  ui.click("nav-creators")
                  val anchor = ui.scroll()
                  ui.click("creator-card")
                  ui.get("detail-pager")
                  ui.pagerRoundtrip()
                  ui.back()
                  ui.until("artist-return-list-restored") { ui.state("browse-list") == anchor }
                  ui.click("creator-card")
                  var suitable = false
                  for (candidate in 0 until 12) {
                    if (
                        ui.probe {
                          Regex("last=(\\d+)")
                              .find(ui.state("page-menu"))
                              ?.groupValues
                              ?.get(1)
                              ?.toIntOrNull()
                              ?.let { it >= 3 } == true
                        }
                    ) {
                      suitable = true
                      break
                    }
                    ui.swipe("detail-pager", forward = true)
                  }
                  check(suitable) { "No live artist with three post pages" }
                  ui.get("post-card")
                  ui.pagination()
                  ui.click("post-card")
                  ui.get("detail-pager")
                  ui.back()
                  for (tab in listOf("Announcements" to "ANNOUNCEMENTS", "Tags" to "TAGS")) {
                    ui.clickLabel(tab.first)
                    ui.until("artist-tab-ready") {
                      val state = ui.state("creator-content")
                      state.contains("tab=${tab.second}") &&
                          state.contains("loading=false") &&
                          state.contains("error=false")
                    }
                  }
                  if (ui.find("artist-tag") != null) {
                    ui.click("artist-tag")
                    ui.get("post-card")
                    ui.back()
                  }
                  ui.back()
                  discoverCommunity(ui)
                },
            "post-page" to { postWorkflow(ui) },
        )
        .apply {
          if (System.getProperty("e2e.case") == "tao-adapter-smoke")
              put("tao-adapter-smoke") {
                ui.click("nav-creators")
                ui.pagination()
              }
        }

private suspend fun popular(ui: TaoUiDriver) {
  ui.click("nav-works")
  ui.clickLabel("Popular", "热门")
  ui.get("post-card")
}

private suspend fun postSearch(ui: TaoUiDriver) {
  ui.click("nav-works")
  ui.clickLabel("Search", "搜索")
  ui.scrollToStart()
  ui.type("search-input", System.getProperty("e2e.query", "commission"))
  ui.click("search-submit")
  searchResults(ui)
}

private suspend fun searchResults(ui: TaoUiDriver) {
  ui.until("search-results-ready") {
    val state = ui.state("search-results")
    state.contains("loading=false") &&
        Regex("count=(\\d+)").find(state)?.groupValues?.get(1)?.toIntOrNull()?.let { it > 0 } ==
            true
  }
  if (ui.find("post-card") == null) ui.scroll()
  ui.get("post-card")
}

private suspend fun dmSearch(ui: TaoUiDriver) {
  ui.click("nav-dm")
  ui.scrollToStart()
  ui.type("search-input", System.getProperty("e2e.dmQuery", "the"))
  ui.click("search-submit")
  ui.get("dm-card")
}

private suspend fun postRoundtrip(ui: TaoUiDriver) {
  val anchor = ui.scroll()
  ui.click("post-card")
  ui.get("detail-pager")
  ui.back()
  ui.until("post-list-restored") { ui.state("browse-list") == anchor }
}

private suspend fun discoverCommunity(ui: TaoUiDriver) {
  ui.click("nav-creators")
  System.getProperty("e2e.communityCreatorName")?.takeIf(String::isNotBlank)?.let {
    ui.type("creator-search-input", it)
    val query = it
    ui.until("community-creator-filtered") {
      ui.find("creator-card")?.let { card -> ui.label(card).contains(query, ignoreCase = true) } ==
          true
    }
  }
  ui.click("creator-card")
  repeat(12) {
    if (ui.probe { ui.hasLabel("Community") }) {
      ui.clickLabel("Community")
      if (
          ui.probe {
            Regex("last=(\\d+)")
                .find(ui.state("page-menu"))
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.let { it >= 3 } == true
          }
      ) {
        ui.pagination()
        ui.back()
        return
      }
    }
    ui.swipe("detail-pager", forward = true)
  }
  error("No live creator with three community pages; required coverage did not pass")
}

private suspend fun postWorkflow(ui: TaoUiDriver) {
  ui.click("nav-works")
  ui.clickLabel("Search", "搜索")
  ui.scrollToStart()
  ui.type("search-input", System.getProperty("e2e.postQuery", "has:image"))
  ui.click("search-submit")
  searchResults(ui)
  repeat(10) {
    val anchor = ui.scroll()
    ui.click("post-card")
    ui.get("detail-pager")
    ui.pagerRoundtrip()
    if (ui.probe { ui.find("image-open") != null }) {
      ui.click("image-open")
      if (!ui.probe(1) { ui.find("image-pager") != null } && ui.find("image-open") != null) {
        ui.click("image-open")
      }
      if (!ui.probe(12) { ui.find("image-pager") != null }) {
        ui.back()
        return@repeat
      }
      val count =
          Regex("count=(\\d+)").find(ui.state("image-pager"))?.groupValues?.get(1)?.toIntOrNull()
              ?: 0
      val loaded =
          count >= 2 &&
              ui.find("zoom-image") != null &&
              ui.probe(12) { ui.state("zoom-image").contains("Success") }
      val load =
          Regex("load=(Idle|Loading|Success|Error)")
              .find(ui.state("zoom-image"))
              ?.groupValues
              ?.get(1) ?: "unavailable"
      val failure =
          Regex("failure=([A-Za-z0-9_:]*)")
              .find(ui.state("zoom-image"))
              ?.groupValues
              ?.get(1)
              .orEmpty()
      println("E2E IMAGE_CANDIDATE count=$count;load=$load;failure=$failure")
      if (loaded) {
        ui.pagerRoundtrip("image-pager")
        ui.zoom()
        ui.back()
        ui.until("post-return-list-restored") { ui.state("browse-list") == anchor }
        return
      }
      ui.click("image-close")
    }
    ui.back()
  }
  error("No live post with multiple zoomable images; required image-pager coverage did not pass")
}
