var mde = {};

$( document ).ready( function() {
	mde.displayObjectList( "dataSets" );
} );

mde.displayObjectList = function() {
	var objectType = $( "#objectType" ).val(),
		url = "../api/" + objectType + "?fields=id,displayName&paging=false";
	
	$.getJSON( url, function( json ) {
		var html = "";
		$.each( json[objectType], function( inx, val ) {
			html += "<option value=\"" + val.id + "\">" + val.displayName + "</option>";
		} );
		
		$( "#objectList" ).html( html );
	} );
}

mde.exportMetadata = function() {
	var objectType = $( "#objectType" ).val(),
			objectId = $( "#objectList" ).val(),
			format = $( "#format" ).val(),
			compression = $("#compression").val();
	var extension =  ( format ? "." + format : "" ) + ( compression ?  "." + compression : "" );
	var	url = "../api/" + objectType + "/" + objectId + "/metadata" + extension;

	url += "?attachment=" + "metadataDependency" + extension;
	window.open( url, "_blank" );
}
