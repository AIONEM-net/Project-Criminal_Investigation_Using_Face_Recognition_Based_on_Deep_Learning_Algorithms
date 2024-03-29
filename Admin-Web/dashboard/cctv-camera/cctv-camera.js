
if(!userID) {
    window.location.replace("../../login/");
}


const criminals = {};

fDatabase.ref('Criminals').on('value', (list) => {

  let i = 0;
  let counts = list.numChildren();
  list.forEach((item) => {

      const id = item.key;
      const data = item.val();

      criminals[data.identity] = data;

  });

  startCCTVCamera();

});

function startCCTVCamera() {

  fDatabase.ref("CCTV-Camera").on("value", (item) => {
    if(item.exists()) {
      const id = item.key;
      let data = item.val();

      
      document.querySelector(".cctv-district").value = data.district ?? "";
      document.querySelector(".cctv-location").value = data.location ?? "";
      document.querySelector(".cctv-time").innerHTML = new Date(data.time).toString().substring(0, 24);

      const criminal = criminals[data.identity] || {};

      if(!criminal.isTracking) {
        data = {};
      }else {

        if(data.isDetected) {
          
        }else {

        }

      }

      document.querySelector(".cctv-image").src = data.photo ?? "../../assets/images/face_scan_1.gif";
      document.querySelector(".cctv-name").innerHTML = data.name ?? "-";
      document.querySelector(".cctv-gender").innerHTML = data.gender ?? "-";
      document.querySelector(".cctv-identity").innerHTML = data.identity ?? "'";
      document.querySelector(".cctv-accuracy").innerHTML = (data.accuracy ?? "-") +" %";

      document.querySelector(".cctv-div").classList.remove("loader");
    }
  });

}


document.querySelector(".cct-location-update").addEventListener("click", function() {

  let district = document.querySelector(".cctv-district").value;
  let location = document.querySelector(".cctv-location").value;

  if(!district) {
    // alert("Select CCTV Camera District")
    // return;
  }
  if(!location) {
    alert("Enter CCTV Camera Location")
    return;
  }

  
  const isYes = confirm(`Do you want to update CCTV Camera Location to "${district +" - "+location}" ?`);

  if(isYes) {
  
      fDatabase.ref('CCTV-Camera/' +'/district').set(district);
      fDatabase.ref('CCTV-Camera/' +'/location').set(location);

  }

});



window.addEventListener('popstate', (event) => {
    history.go(1);
});
history.pushState({ state: 1 }, '');
