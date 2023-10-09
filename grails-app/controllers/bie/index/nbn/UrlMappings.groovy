package bie.index.nbn

class UrlMappings {

    static mappings = {
        "/internal/search(.$format)?"(controller: "NBNSearch", action: "searchInternal")
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }


    }
}
