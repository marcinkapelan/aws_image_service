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
            images: [],
            selectedImages: false
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
                response.data.forEach((item) => {
                        let fullName = item.url.match(".amazonaws.com\/(.*)\\?X-Amz-Algorithm")[1];
                        let name = fullName.substring(fullName.indexOf("_") + 1);

                        let imageData = {
                            src: item.url,
                            thumbnail: item.url,
                            thumbnailWidth: 400,
                            thumbnailHeight: 300,
                            isSelected: false,
                            caption: name + " Size: " + Math.floor(item.size / 1024) + "KB",
                            size: item.size,
                            name: fullName
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
        img.isSelected = !img.isSelected;

        this.setState({
            images: images
        }, () => {
            let num = 0;
            this.state.images.forEach((item) => {
                if (item.isSelected) {
                    num++;
                }
            });
            this.setState({
                selectedImages: num > 0
            })
        })
    };

    OnResizeClickHandler = () => {
        const MAX_FILESSIZE_PER_MESSAGE = 25000000;
        let messages = [];

        let filesSizeSum = 0;
        let message = {command: "RESIZE", files: []};
        this.state.images.forEach((item) => {
            if (item.isSelected) {
                if (filesSizeSum + item.size > MAX_FILESSIZE_PER_MESSAGE) {
                    messages.push(message);
                    message = {command: "RESIZE", files: []};
                }

                message.files.push({
                    name: item.name,
                    size: item.size,
                });
                filesSizeSum += item.size;
            }
        });
        if (message.files.length > 0) {
            messages.push(message);
        }
        axios.post(baseUrl + "/queue", messages)
            .then(response => {
                alert("Request delivered successfully");
                console.log(response);
            })
            .catch(error => {
                alert("Error:" + error);
                console.log(error);
            })
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
                <div className="row">
                    <div className="col-md-1">
                        <input type = "file" name = "file" onChange = {this.onFileChangeHandler}/>
                    </div>
                    <div className="col-md-1 offset-md-2">
                        <button type="button" className="btn btn-success" onClick = {this.onUploadClickHandler}>Upload</button>
                    </div>
                    <div className="col-md-1 offset-md-7">
                        <button type="button" className="btn btn-primary" disabled={!this.state.selectedImages} onClick = {this.OnResizeClickHandler}>Resize</button>
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