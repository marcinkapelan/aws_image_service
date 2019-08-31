import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min';
import React, { Component } from 'react';
import axios from 'axios';
import Gallery from "react-grid-gallery";

const baseUrl = "http://localhost:8080";

class App extends Component {

    constructor(props) {
        super(props);
        this.state = {
            selectedFile: null,
            images: []
        }
        this.fetchImageUrlsFromS3 = this.fetchImageUrlsFromS3.bind(this);
    };

    componentDidMount() {
        this.fetchImageUrlsFromS3()
    }

    fetchImageUrlsFromS3() {
        let imagesUrls = [];
        this.setState({
            images: []
        });
        axios.get(baseUrl + "/presignedurls")
            .then((response) => {
                console.log(response);
                //alert(response.data.length);
                response.data.forEach((item) => {
                        let imageFullName = item.match(".amazonaws.com\/(.*)\\?X-Amz-Algorithm")[1];
                        let imageName = imageFullName.substring(imageFullName.indexOf("_") + 1);

                        let imageData = {
                            src: item,
                            thumbnail: item,
                            thumbnailWidth: 400,
                            thumbnailHeight: 300,
                            isSelected: false,
                            caption: imageName,
                        }

                        this.setState({
                            images: this.state.images.concat(imageData)
                        })
                    }
                );
            })
            .catch((error) => {
                console.log(error);
            });
    }

    onImageSelected = (index, image) => {
        var images = this.state.images.slice();
        var img = images[index];
        if(img.hasOwnProperty("isSelected"))
            img.isSelected = !img.isSelected;
        else
            img.isSelected = true;

        this.setState({
            images: images
        });
    }

    onFileChangeHandler = event => {
        console.log(event.target.files[0]);
        this.setState({
            selectedFile: event.target.files[0]
        })
    };

    onUploadClickHandler = event => {
        if (this.state.selectedFile == null) {
            alert("No file has been chosen");
        }
        else {
            axios.get(baseUrl + "/presignedpost", {
                params: {
                    fileName: this.state.selectedFile.name,
                }
            })
                .then(response => {
                    this.postImageToS3(response.data)
                })
                .catch(error => {
                    alert(error);
                    console.log(error);
                });
        }
    }

    postImageToS3(postData) {
        console.log("postData.url: " + postData.url)
        //alert(this.state.selectedFile.type);
        const formData = new FormData()
        Object.keys(postData.fields).forEach(key => {
            formData.append(key, postData.fields[key]);
        });
        formData.append("file", this.state.selectedFile);
        axios.post(postData.url, formData)
            .then(response => {
                this.fetchImageUrlsFromS3();
                alert("Upload successful");
                console.log(response);
            })
            .catch(error => {
                alert("Error:" + error);
                console.log(error);
            });
    }

    render() {
        return (
            <div className="container">
                <div className="row justify-content-between">
                    <div className="mr-5 col-md-1">
                        <input type = "file" name = "file" onChange = {this.onFileChangeHandler}/>
                    </div>
                    <div className="ml-5 col-md-1">
                        <button type="button" className="btn btn-success" onClick = {this.onUploadClickHandler}>Upload</button>
                    </div>
                </div>
                <div className="mt-3 row">
                    <div className="col-md-12">
                        <Gallery images={this.state.images} onSelectImage={this.onImageSelected}/>
                    </div>
                </div>
            </div>
        );
    }

}
export default App;