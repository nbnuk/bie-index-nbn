$( document ).ready(function() {

    // var jsFileLocation = $('script[src*="/assets/nbn.js"]').attr('src');
    // jsFileLocation = jsFileLocation.substring(0,jsFileLocation.lastIndexOf("/"));
    // $.getScript(jsFileLocation+'/application-last.js');

    customise_admin_index_page();
    customise_import_occurrences_page();

    function customise_admin_index_page() {
        let h2 = $("#page-body h2")
        if (!h2 || h2.text().indexOf("Admin")<0) { //unique identifier for home page.
            return;
        }


        $("#page-body ul li:last").before('<li><a href="/admin/import/occurrencesplaces">Import Places occurrences (for featured region)</a> Augment places with occurrences data - index number of records.</li>');
        $("#page-body ul li:last").before('<li><a href="/admin/import/speciescountsplaces">Import Places species counts (for featured region)</a> Augment places with species counts - index number of records .</li>');
        $("#page-body ul li:last").remove();



    }

    function customise_import_occurrences_page() {
        let h2 = $("#main h2")
        if (!h2 || h2.text().indexOf("Import Occurrences")<0) { //unique identifier for home page.
            return;
        }

        let buttonContainer = $("button#start-import").parent();

        $("button#start-import").remove();
        buttonContainer.html(generate_button("start-import", false));
        buttonContainer.append(generate_button("start-import2", true));

        function generate_button(id, online) {
            return '<p style="margin-bottom:2rem"><button id="'+id+'" onclick="javascript:loadInfo(\'/admin/import/importOccurrences?online="+online+"\')" class="btn '+(online?"btn-danger":"btn-primary")+' import-button">Add occurrence information '+(online?" into ONLINE index (WARNING)":" into offline index")+'</button></p>';
        }
    }


})