import { run, type FixtureTarget } from "./fixture-tools";

const targets: FixtureTarget[] = [
  {
    file: "creator-last.html",
    path: "/patreon/user/3295915?o=200",
  },
  {
    file: "creator-community.html",
    path: "/patreon/user/3327936/community/8d335b0104b74ab4a399a26456870cbc",
  },
  {
    file: "posts-search-syntax.html",
    path: "/posts?q=intitle%3A%22full%20reaction%22%20after%3A2026-01-01%20before%3A2026-12-01%20service%3Apatreon%20has%3Avideo%20-trailer",
    format: "html",
  },
  {
    file: "post-temporary-images.html",
    path: "/patreon/user/49659762/post/176021808",
    format: "html",
  },
  {
    file: "post-temporary-images.json",
    path: "/api/v1/patreon/user/49659762/post/176021808",
    format: "json",
  },
  {
    file: "post-temporary-video.html",
    path: "/patreon/user/16917716/post/176023581",
    format: "html",
  },
  {
    file: "post-temporary-video.json",
    path: "/api/v1/patreon/user/16917716/post/176023581",
    format: "json",
  },
  {
    file: "post-temporary-archive.html",
    path: "/patreon/user/193025837/post/176015142",
    format: "html",
  },
  {
    file: "post-temporary-archive.json",
    path: "/api/v1/patreon/user/193025837/post/176015142",
    format: "json",
  },
  {
    file: "post-unavailable-original.html",
    path: "/patreon/user/154079163/post/168249687",
    format: "html",
  },
  {
    file: "post-unavailable-original.json",
    path: "/api/v1/patreon/user/154079163/post/168249687",
    format: "json",
  },
  {
    file: "home.html",
    path: "/",
  },
  {
    file: "posts.html",
    path: "/posts",
  },
  {
    file: "posts-search.html",
    path: "/posts?q=test",
  },
  {
    file: "posts-advanced-search.html",
    path: "/posts?q=intitle%3Awolf+after%3A2026-01-01+has%3Aimage",
  },
  {
    file: "posts-service.html",
    path: "/posts?service=patreon",
  },
  {
    file: "posts-tag.html",
    path: "/posts?tag=nsfw",
  },
  {
    file: "posts-popular.html",
    path: "/posts/popular",
  },
  {
    file: "posts-tags.html",
    path: "/posts/tags",
  },
  {
    file: "dms.html",
    path: "/dms",
  },
  {
    file: "dms-search.html",
    path: "/dms?q=search-test",
  },
  {
    file: "creator.html",
    path: "/patreon/user/3295915",
  },
  {
    file: "creator-tags.html",
    path: "/patreon/user/3295915/tags",
  },
  {
    file: "creator-recommended.html",
    path: "/patreon/user/3295915/recommended",
  },
  {
    file: "announcements.html",
    path: "/announcements",
  },
];

await run("https://pawchive.pw", targets);
