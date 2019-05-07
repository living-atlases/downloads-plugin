package au.org.ala.downloads

import grails.converters.JSON
import groovy.util.slurpersupport.GPathResult
import grails.web.servlet.mvc.GrailsParameterMap

class UtilityService {

    def grailsApplication, biocacheService

    /**
     * Retrieve the download fields mapped to the vairous DwC classes from https://biocache.ala.org.au/ws/index/fields
     *
     * @return Map<String,List> classesMap
     */
    Map getFieldGroupMap() {
        Map fieldGroupMap = [:]
        List dwcClasses = grailsApplication.config.downloads.customSections.darwinCore ?: []

        dwcClasses.each { cls ->
            fieldGroupMap.put(cls, biocacheService.getFieldsForDwcClass(cls))
        }

        fieldGroupMap.put("conservationStatus", grailsApplication.config.downloads.conservationStatus)
        fieldGroupMap.put("otherTraits", grailsApplication.config.downloads.otherTraits)
        fieldGroupMap.put("environmentalLayers", ["All elxxxx (environmental) fields"])
        fieldGroupMap.put("contextualLayers", ["All clxxxx (contextual) fields"])
        fieldGroupMap.put("qualityAssertions", ["All QA fields - <a href='https://biocache-ws.ala.org.au/ws/occurrences/search?q=*:*&facets=assertions&pageSize=0&flimit=500' target='_blank'>see the full list</a>"])
        fieldGroupMap.put("miscellaneousFields", ["All miscellaneous fields"])

        fieldGroupMap
    }

    /**
     * Parses the TDWG Darwin Core Schema file and extracts the DwC "classes" and their child "terms"
     * and returns a Map<String, List> with the class as the key and list of terms as the value.
     *
     * @deprecated - keeping for reference only
     * @return Map<String,List> classesMap
     */
    Map getDwcClassesAndTerms() {
        GPathResult schema
        Map classesMap  = [:]
        String schemaUrl = grailsApplication.config.downloads.dwcSchemaUrl

        try {
            schema = new XmlSlurper().parse(schemaUrl)
        } catch (Exception ex) {
            log.warn "Problem reading & parsing DwC schema XML (${schemaUrl}: ${ex}", ex
        }

        if (schema) {
            schema.group.each { group ->
                String name = group.@'name'
                name = name.replace("Terms","")
                classesMap[name] = group.sequence.element.collect{ "${it.@'ref'}" }
            }
        }

        log.debug "classesMap = ${classesMap.keySet() as JSON}"
        classesMap
    }

    /**
     * Wrapper for an input List (of Objects with property names to be used for sorting),
     * in order to generate a sublist suitable for pagination
     * using the Grails paginate taglib, via the params object.
     *
     * Supports the folowwing params:
     *  - max (int)
     *  - offset (int)
     *  - sort (string)
     *  - order (ASC or DESC)
     *
     * @param results
     * @param params
     * @return
     */
    List paginateWrapper(List results, GrailsParameterMap params) {
        int max = params.getInt("max") ?: 10
        int offset = params.getInt("offset") ?: 0
        int toIndex = ((offset + max) < results.size()) ? (offset + max) : results.size()
        def sort = params.sort
        def order = params.order ?: 'ASC'

        if (offset > toIndex) {
            // if offset is greater than toIndex, show last page
            offset = toIndex - (toIndex % max)
        }

        if (sort) {
            results = results.sort { it[params.sort]?.toLowerCase() }
        }

        if (order == 'DESC') {
            results = results.reverse()
        }
        // get the requested items for pagination
        results.subList(offset,toIndex)
    }

    /**
     * Get the Boolean version of a config variable even if the original is in a String form
     *
     * @param value
     * @return
     */
    Boolean getBooleanValue(def value) {
        if (value.class == java.lang.Boolean) {
            // catch Boolean but not boolean - 'true.toBoolean() == false' !!!
            return value
        } else {
            return (value as String).toBoolean()
        }
    }
}
