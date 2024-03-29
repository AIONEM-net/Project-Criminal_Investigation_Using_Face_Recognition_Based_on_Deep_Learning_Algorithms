
if(!userID) {
    window.location.replace("../../login/");
}

document.querySelector("tbody").innerHTML = "";
document.querySelector(".table-responsive").classList.add("loader");

let isDataTable = true;
fDatabase.ref('Detections').on('value', (list) => {

    let html = "";
    let i = 0;
    let counts = list.numChildren();
    list.forEach((item) => {

        const id = item.key;
        const data = item.val();

        i++;
        let html1 = `
            <tr>
                <td>
                    ${i}
                </td>
                <td>
                    <img src="${data.photo ?? '../../assets/images/logo_face.jpg'} alt=""/>
                </td>
                <td>
                    ${data.name ?? ''}
                </td>
                <td class="text-center">
                    ${data.identity ?? ''}
                </td>
                <td class="text-center">
                    ${data.gender ?? ''}
                </td>
                <td class="text-center">
                    <label class="badge text-center ${data.accuracy > 66 ? 'badge-success' : data.accuracy > 33 ? 'badge-warning' : 'badge-danger' }">
                        ${data.accuracy ?? '-'} %
                    </label>
                </td>
                <td class="text-center">
                    ${data.district ?? ''} ${data.location ?? ''}
                </td>
                <td class="text-center">
                    ${new Date(data.time).toString().substring(0, 24)}
                </td>
                <td class="text-center">
                    <a class="btn btn-danger btn-sm font-weight-medium text-white"
                       onclick="onReportDelete('${id}', '${data.name}');">
                        <i class="fa fa-trash"></i>
                        Delete
                    </a>
                </td>
            </tr>
        `;

        html = html1 + html;
        
    });

    document.querySelector("tbody").innerHTML = html;

    if(isDataTable) {
        $('.table').DataTable({
        dom: 'Bfrtip',
        buttons: [
            {
                extend: 'excelHtml5',
                text: '<i class="fa fa-file-excel-o"></i> Excel',
                exportOptions: {
                    columns: ':not(.no-export)'
                }
            },
            {
                extend: 'pdfHtml5',
                text: '<i class="fa fa-file-pdf-o"></i> PDF',
                exportOptions: {
                    columns: ':not(.no-export)'
                }
            },
            {
                extend: 'print',
                text: '<i class="fa fa-print"></i> PRINT',
                exportOptions: {
                    columns: ':not(.no-export)'
                }
            },
        ]
        });
        isDataTable = false;
    }

    document.querySelector(".table-responsive").classList.remove("loader");

});

function onReportDelete(id, name) {

    const isYes = confirm(`Do you want to DELETE "${name}" ?`);

    if(isYes) {
    
        fDatabase.ref('Detections/'+ id).remove();

    }

}

function clearAllReports() {

    const isYes = confirm(`Do you want to DELETE All Reports ?`);

    if(isYes) {
    
        fDatabase.ref('Detections').remove();

        document.querySelector(".table-responsive").classList.remove("loader");

    }

}
