$( document ).ready(function() {

    // var jsFileLocation = $('script[src*="/assets/nbn.js"]').attr('src');
    // jsFileLocation = jsFileLocation.substring(0,jsFileLocation.lastIndexOf("/"));
    // $.getScript(jsFileLocation+'/application-last.js');

    customise_admin_index_page();
    customise_import_occurrences_page();

    function customise_admin_index_page() {
        var h2 = $("#page-body h2")
        if (!h2 || h2.text().indexOf("Admin")<0) { //unique identifier for home page.
            return;
        }
        var regions, occurrences;
        $("#page-body ul li").each(function() {
            if ($(this).html().indexOf("Import Regions")>-1) {
                regions = this;
            } else if ($(this).html().indexOf("Import Occurrences")>-1) {
                occurrences = this;
            }
        });

        if (regions) {
            $(regions).after('<li><a href="/admin/import/featuredregions">Import Featured Regions</a> - BBG burial grounds</li>');
        }

        if (occurrences) {
            $(occurrences).after('<li><a href="/admin/import/speciescountsplaces">Import Places Species Counts (for featured region)</a> Augment places with species counts - index number of records .</li>');
            $(occurrences).after('<li><a href="/admin/import/occurrencesplaces">Import Places Occurrences (for featured region)</a> Augment places with occurrences data - index number of records.</li>');
        }

        $("#page-body ul li:last").remove();



    }

    function customise_import_occurrences_page() {
        var h2 = $("#main h2")
        if (!h2 || h2.text().indexOf("Import Occurrences")<0) { //unique identifier for home page.
            return;
        }

        var buttonContainer = $("button#start-import").parent();

        $("button#start-import").remove();
        buttonContainer.html(generate_button("start-import", false));
        buttonContainer.append(generate_button("start-import2", true));

        function generate_button(id, online) {
            var importUrl = "/admin/import/importOccurrences?online="+online;
            return '<p style="margin-bottom:2rem"><button id="'+id+'" onclick="javascript:loadInfo(\''+importUrl+'\')" class="btn '+(online?"btn-danger":"btn-primary")+' import-button">Add occurrence information '+(online?" into ONLINE index (WARNING)":" into offline index")+'</button></p>';
        }
    }


})