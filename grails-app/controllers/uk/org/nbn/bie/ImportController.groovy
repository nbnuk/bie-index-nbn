package uk.org.nbn.bie

import au.org.ala.web.AlaSecured
import org.apache.commons.lang.BooleanUtils

@AlaSecured(value = "ROLE_ADMIN", redirectUri = "/")
class ImportController extends au.org.ala.bie.ImportController{

    def importService

    def occurrencesplaces(){}

    def speciescountsplaces(){}

    def featuredregions(){}

    def index() {}

    /**
     * Index place occurrence data
     *
     * @return
     */
    def importOccurrencesPlaces(){
        log.debug("importOccurrencesPlaces")
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        def job = execute("importOccurrencesPlaces", "admin.button.loadoccurrenceplaces", { importService.importOccurrenceDataForPlaces(online) })
        asJson (job.status())

    }

    /**
     * Index place species count data
     *
     * @return
     */
    def importSpeciesCountsForPlaces(){
        log.debug("importSpeciesCountsForPlaces")
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        def job = execute("importSpeciesCounts", "admin.button.loadspeciescountsplaces", { importService.importSpeciesCounts(online) })
        asJson (job.status())

    }

    /**
     * Index featured regions (for places)
     *
     * @return
     */
    def importFeaturedRegions(){
        log.debug("importFeaturedRegions")
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        def job = execute("importFeaturedRegions", "admin.button.importfeaturedregions", { importService.importFeaturedRegions() })
        asJson (job.status())

    }


}
