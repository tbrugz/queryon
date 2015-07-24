
function showErrorMessages(messagesId, text) {
	var msg = document.getElementById(messagesId);
	$('#'+messagesId).html("<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>");
	$('#'+messagesId).attr('class','error');
	updateUI();
}

function closeMessages(messagesId) {
	document.getElementById(messagesId).innerHTML = '';
	updateUI();
}

