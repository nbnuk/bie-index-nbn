package bie.index.nbn

class UrlMappings {

    static mappings = {

        "/sitemap.xml"(controller: "sitemap", action: "index")
        "/sitemap$idx.xml"(controller: "sitemap", action: "index")

        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }


    }
}
