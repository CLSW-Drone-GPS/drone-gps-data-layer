var map;
var json;
let svg2 = d3.select('svg2') ;

async function getZones(lat, long) {
    url = "https://api.cquest.org/drone?lat="
    long_url= "&lon="
    url_end = "&rayon=5000&limite=50"
    const response = await fetch(url+lat+long_url+long+url_end);
    const result= await response.json();
    return result
}


async function getDate() {

    var bod =JSON.stringify({"dataSource" : "DroneApp" ,"database" : "geo_local" ,"collection" : "all" });
    var url = 'https://data.mongodb-api.com/app/data-pwjwq/endpoint/data/v1/action/find';

    try{
        const response =await fetch(url, {
            method: 'POST',
            Header: {
                "Accept": "application/json",
                "Content-Type" : "application/json",
                'Access-Control-Allow-Origin:' : "*",
                "api-key": "637724584dbec05fc7d3101f",
                "Access-Control-Request-Headers" : "*"
            },
            body: bod
        });

        const result = await response.json();
        return result;
        }catch (err) {
        console.log(err);
    }
}

getDate().then(function(result) {
    svg2.selectAll("*").remove();
    d3.select('svg2').selectAll("span")
        .data(result.documents)
        .enter()
        .append("button" )
        .text(function(d) { return d.date  })
        .on("click", click)

});


async function click(d, i) {
    lat =d.lat
    long = d.long
   initMap(lat, long);
}


function initMap(lat, long) {
    map = new google.maps.Map(document.getElementById('map'), {
        center: {lat: lat, lng: long},
        zoom: 12
    });
    var featureStyle = {
        strokeColor: '#8b0ee8',
        strokeWeight: 4
    }
    getZones(lat, long).then(function(result) {
        result.type = 'FeatureCollection';
        json = result
        map.data.addGeoJson(json);
        map.data.setStyle(featureStyle);
    });
}






















